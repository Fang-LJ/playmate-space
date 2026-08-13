package com.playmate.space.service.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.entity.ActivityExpenseEntity;
import com.playmate.space.entity.ActivityExpenseShareEntity;
import com.playmate.space.mapper.ActivityExpenseMapper;
import com.playmate.space.mapper.ActivityExpenseShareMapper;
import com.playmate.space.service.ActivityFinanceStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanceSnapshotLoader {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ActivityFinanceStateService financeStateService;
    private final ActivityExpenseMapper expenseMapper;
    private final ActivityExpenseShareMapper shareMapper;

    public FinanceSnapshotLoader(ActivityFinanceStateService financeStateService,
                                 ActivityExpenseMapper expenseMapper,
                                 ActivityExpenseShareMapper shareMapper) {
        this.financeStateService = financeStateService;
        this.expenseMapper = expenseMapper;
        this.shareMapper = shareMapper;
    }

    /**
     * Kept in a dedicated bean so Spring applies the consistent read transaction through its proxy.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SettlementSnapshot load(Long activityId) {
        long financeVersion = financeStateService.currentVersion(activityId);
        List<ActivityExpenseEntity> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ActivityExpenseEntity>()
                .eq(ActivityExpenseEntity::getActivityId, activityId)
                .eq(ActivityExpenseEntity::getStatus, "ACTIVE")
                .orderByDesc(ActivityExpenseEntity::getExpenseTime)
                .orderByDesc(ActivityExpenseEntity::getId));
        Set<Long> expenseIds = expenses.stream().map(ActivityExpenseEntity::getId).collect(Collectors.toSet());
        List<ActivityExpenseShareEntity> shares = expenseIds.isEmpty() ? List.of() : shareMapper.selectList(
                new LambdaQueryWrapper<ActivityExpenseShareEntity>().in(ActivityExpenseShareEntity::getExpenseId, expenseIds));
        Map<Long, List<ActivityExpenseShareEntity>> sharesByExpense = shares.stream()
                .collect(Collectors.groupingBy(ActivityExpenseShareEntity::getExpenseId));

        Map<Long, MutableAccount> accounts = new TreeMap<>();
        expenses.forEach(expense -> account(accounts, expense.getPayerUserId()).paid = plus(
                account(accounts, expense.getPayerUserId()).paid, expense.getAmount()));
        shares.forEach(share -> account(accounts, share.getUserId()).share = plus(
                account(accounts, share.getUserId()).share, share.getShareAmount()));
        accounts.values().forEach(MutableAccount::finish);

        List<SettlementSnapshot.Account> snapshotAccounts = accounts.values().stream()
                .map(account -> new SettlementSnapshot.Account(account.userId, account.paid, account.share, account.net))
                .toList();
        List<SettlementSnapshot.Suggestion> suggestions = suggestions(accounts);
        List<SettlementSnapshot.RecentExpense> recentExpenses = expenses.stream().limit(2).map(expense ->
                new SettlementSnapshot.RecentExpense(expense.getId(), expense.getTitle(), expense.getCategory(),
                        money(expense.getAmount()), expense.getPayerUserId(), expense.getExpenseTime(), expense.getVersion(),
                        sharesByExpense.getOrDefault(expense.getId(), List.of()).stream()
                                .map(share -> new SettlementSnapshot.Share(share.getUserId(), money(share.getShareAmount())))
                                .toList())
        ).toList();
        BigDecimal totalExpense = expenses.stream().map(ActivityExpenseEntity::getAmount)
                .reduce(ZERO, FinanceSnapshotLoader::plus);
        return new SettlementSnapshot(SettlementSnapshot.SCHEMA_VERSION, activityId, financeVersion, totalExpense,
                expenses.size(), snapshotAccounts.size(), snapshotAccounts, suggestions, recentExpenses);
    }

    private static MutableAccount account(Map<Long, MutableAccount> accounts, Long userId) {
        return accounts.computeIfAbsent(userId, MutableAccount::new);
    }

    private static List<SettlementSnapshot.Suggestion> suggestions(Map<Long, MutableAccount> accounts) {
        List<MutableAccount> debtors = accounts.values().stream().filter(item -> item.net.signum() < 0)
                .map(MutableAccount::copy).toList();
        List<MutableAccount> creditors = accounts.values().stream().filter(item -> item.net.signum() > 0)
                .map(MutableAccount::copy).toList();
        List<SettlementSnapshot.Suggestion> result = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            MutableAccount debtor = debtors.get(debtorIndex);
            MutableAccount creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.net.abs().min(creditor.net);
            if (amount.signum() > 0) result.add(new SettlementSnapshot.Suggestion(debtor.userId, creditor.userId, money(amount)));
            debtor.net = plus(debtor.net, amount);
            creditor.net = plus(creditor.net, amount.negate());
            if (debtor.net.signum() == 0) debtorIndex++;
            if (creditor.net.signum() == 0) creditorIndex++;
        }
        return result;
    }

    private static BigDecimal plus(BigDecimal first, BigDecimal second) { return normalize(first.add(second)); }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY); }

    private static BigDecimal normalize(BigDecimal value) {
        return value.abs().compareTo(new BigDecimal("0.005")) < 0 ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class MutableAccount {
        private final Long userId;
        private BigDecimal paid = ZERO;
        private BigDecimal share = ZERO;
        private BigDecimal net = ZERO;

        private MutableAccount(Long userId) { this.userId = userId; }
        private void finish() { net = normalize(paid.subtract(share)); }
        private MutableAccount copy() { MutableAccount copy = new MutableAccount(userId); copy.net = net; return copy; }
    }
}
