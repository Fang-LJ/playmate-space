package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.dto.expense.*;
import com.playmate.space.entity.*;
import com.playmate.space.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SettlementService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String CALCULATION_RULE = "结算净额 = 实际付款 - 应承担";

    private final ActivityCollaborationAccess access;
    private final ActivityExpenseMapper expenseMapper;
    private final ActivityExpenseShareMapper shareMapper;
    private final ActivitySettlementMapper settlementMapper;
    private final ActivityMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ActivityFinanceStateService financeStateService;

    public SettlementService(ActivityCollaborationAccess access, ActivityExpenseMapper expenseMapper,
                             ActivityExpenseShareMapper shareMapper, ActivitySettlementMapper settlementMapper,
                             ActivityMemberMapper memberMapper, UserMapper userMapper,
                             ActivityFinanceStateService financeStateService) {
        this.access = access;
        this.expenseMapper = expenseMapper;
        this.shareMapper = shareMapper;
        this.settlementMapper = settlementMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.financeStateService = financeStateService;
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse expenseSummary(Long activityId) {
        Long userId = requireExpenseAccess(activityId);
        return toExpenseSummary(calculate(activityId), userId);
    }

    @Transactional(readOnly = true)
    public ExpenseDashboardResponse dashboard(Long activityId) {
        requireExpenseAccess(activityId);
        Calculation calculation = calculate(activityId);
        List<ExpenseDashboardMemberResponse> members = calculation.accounts.values().stream()
                .map(account -> dashboardMember(account, calculation.users.get(account.userId)))
                .toList();
        return new ExpenseDashboardResponse(
                new ExpenseDashboardSummaryResponse(calculation.totalExpense, calculation.expenses.size(), calculation.accounts.size()),
                members,
                calculation.suggestions,
                CALCULATION_RULE,
                calculation.financeVersion
        );
    }

    /** Legacy V2-compatible endpoint. V1 pages use dashboard() and do not expose transfer state. */
    public SettlementSummaryResponse summary(Long activityId) {
        Long userId = requireExpenseAccess(activityId);
        Calculation calculation = calculate(activityId);
        List<SettlementHistoryResponse> history = historyRows(activityId);
        return new SettlementSummaryResponse(
                toExpenseSummary(calculation, userId),
                legacySuggestions(calculation.suggestions, userId),
                history,
                legacyMembers(calculation, history, userId),
                calculation.totalExpense,
                calculation.expenses.size(),
                CALCULATION_RULE
        );
    }

    /** Legacy V2-compatible endpoint. */
    public List<ExpenseMemberResponse> members(Long activityId) {
        Long userId = requireExpenseAccess(activityId);
        Calculation calculation = calculate(activityId);
        return legacyMembers(calculation, historyRows(activityId), userId);
    }

    /** Legacy V2-compatible endpoint. */
    public List<SettlementHistoryResponse> history(Long activityId) {
        requireExpenseAccess(activityId);
        return historyRows(activityId);
    }

    /** Used before removing a member. Historical transfer records intentionally do not affect this value. */
    public BigDecimal remainingNet(Long activityId, Long userId) {
        Account account = calculate(activityId).accounts.get(userId);
        return account == null ? ZERO : account.net;
    }

    /** Legacy V2-compatible endpoint. It does not alter V1 real-time balances. */
    @Transactional
    public SettlementHistoryResponse complete(Long activityId, CompleteSettlementRequest request) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        if (request.fromUserId().equals(request.toUserId())) throw param("付款人与收款人不能相同");
        BigDecimal amount = money(request.amount());
        boolean valid = calculate(activityId).suggestions.stream().anyMatch(item ->
                item.fromUserId().equals(request.fromUserId())
                        && item.toUserId().equals(request.toUserId())
                        && item.amount().compareTo(amount) == 0);
        if (!valid) throw new BusinessException(ErrorCode.BUSINESS_ERROR.code(), "当前结算建议已变化，请刷新后重试");
        boolean creator = access.isActivityCreator(activity, member, userId);
        if (!creator && !userId.equals(request.fromUserId())) throw new ForbiddenException("仅付款人可以标记已转账");

        LocalDateTime now = LocalDateTime.now();
        ActivitySettlementEntity entity = new ActivitySettlementEntity();
        entity.setActivityId(activityId);
        entity.setFromUserId(request.fromUserId());
        entity.setToUserId(request.toUserId());
        entity.setAmount(amount);
        entity.setStatus("COMPLETED");
        entity.setCompletedAt(now);
        entity.setOperatedBy(userId);
        entity.setRemark(trim(request.remark()));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleteFlag(0);
        settlementMapper.insert(entity);
        return historyItem(entity, userMap(Set.of(entity.getFromUserId(), entity.getToUserId())));
    }

    /** Legacy V2-compatible endpoint. */
    @Transactional
    public SettlementHistoryResponse cancel(Long activityId, Long settlementId, CancelSettlementRequest request) {
        Long userId = access.requireUserId();
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        ActivitySettlementEntity entity = settlementMapper.selectById(settlementId);
        if (entity == null || !activityId.equals(entity.getActivityId())) throw new NotFoundException("转账记录不存在");
        if (!"COMPLETED".equals(entity.getStatus())) throw param("仅已完成转账可以撤销");
        if (!userId.equals(entity.getOperatedBy()) && !access.isActivityCreator(activity, member, userId)) {
            throw new ForbiddenException("无权撤销该转账记录");
        }
        entity.setStatus("CANCELED");
        entity.setCanceledAt(LocalDateTime.now());
        entity.setCanceledBy(userId);
        entity.setCancelReason(trim(request == null ? null : request.reason()));
        entity.setUpdateTime(LocalDateTime.now());
        settlementMapper.updateById(entity);
        return historyItem(entity, userMap(Set.of(entity.getFromUserId(), entity.getToUserId())));
    }

    Calculation calculate(Long activityId) {
        List<ActivityExpenseEntity> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ActivityExpenseEntity>()
                .eq(ActivityExpenseEntity::getActivityId, activityId)
                .eq(ActivityExpenseEntity::getStatus, "ACTIVE")
                .orderByDesc(ActivityExpenseEntity::getExpenseTime)
                .orderByDesc(ActivityExpenseEntity::getId));
        Set<Long> expenseIds = expenses.stream().map(ActivityExpenseEntity::getId).collect(Collectors.toSet());
        List<ActivityExpenseShareEntity> shares = expenseIds.isEmpty()
                ? List.of()
                : shareMapper.selectList(new LambdaQueryWrapper<ActivityExpenseShareEntity>()
                .in(ActivityExpenseShareEntity::getExpenseId, expenseIds));

        Set<Long> userIds = new TreeSet<>();
        expenses.forEach(item -> userIds.add(item.getPayerUserId()));
        shares.forEach(item -> userIds.add(item.getUserId()));
        Map<Long, UserEntity> users = userMap(userIds);
        Map<Long, Account> accounts = new TreeMap<>();
        userIds.forEach(id -> accounts.put(id, new Account(id)));
        expenses.forEach(item -> accounts.get(item.getPayerUserId()).paid =
                plus(accounts.get(item.getPayerUserId()).paid, item.getAmount()));
        shares.forEach(item -> accounts.get(item.getUserId()).share =
                plus(accounts.get(item.getUserId()).share, item.getShareAmount()));
        accounts.values().forEach(Account::finish);

        Map<Long, List<ActivityExpenseShareEntity>> sharesByExpense = shares.stream()
                .collect(Collectors.groupingBy(ActivityExpenseShareEntity::getExpenseId));
        BigDecimal totalExpense = expenses.stream().map(ActivityExpenseEntity::getAmount)
                .reduce(ZERO, SettlementService::plus);
        return new Calculation(expenses, sharesByExpense, accounts, users,
                suggestions(accounts, users), totalExpense, financeStateService.currentVersion(activityId));
    }

    private Long requireExpenseAccess(Long activityId) {
        Long userId = access.requireUserId();
        access.requireActivity(activityId);
        access.requireActiveMember(activityId, userId);
        return userId;
    }

    private ExpenseSummaryResponse toExpenseSummary(Calculation calculation, Long currentUserId) {
        Account account = calculation.accounts.get(currentUserId);
        BigDecimal net = account == null ? ZERO : account.net;
        List<ExpenseSuggestionResponse> mine = calculation.suggestions.stream()
                .filter(item -> item.fromUserId().equals(currentUserId) || item.toUserId().equals(currentUserId))
                .toList();
        return new ExpenseSummaryResponse(
                net,
                settlementText(net),
                mine,
                calculation.suggestions.size(),
                calculation.expenses.size(),
                listItems(calculation.expenses.stream().limit(2).toList(), calculation.sharesByExpense,
                        calculation.users, currentUserId),
                calculation.financeVersion
        );
    }

    private ExpenseDashboardMemberResponse dashboardMember(Account account, UserEntity user) {
        return new ExpenseDashboardMemberResponse(account.userId, nickname(user),
                user == null ? null : user.getAvatarUrl(), account.paid, account.share,
                account.net, settlementText(account.net));
    }

    private List<ExpenseSuggestionResponse> suggestions(Map<Long, Account> accounts, Map<Long, UserEntity> users) {
        List<Account> debtors = accounts.values().stream()
                .filter(item -> item.net.compareTo(ZERO) < 0)
                .sorted(Comparator.comparing(item -> item.userId))
                .map(Account::copy)
                .toList();
        List<Account> creditors = accounts.values().stream()
                .filter(item -> item.net.compareTo(ZERO) > 0)
                .sorted(Comparator.comparing(item -> item.userId))
                .map(Account::copy)
                .toList();
        List<ExpenseSuggestionResponse> result = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Account debtor = debtors.get(debtorIndex);
            Account creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.net.abs().min(creditor.net);
            if (amount.compareTo(ZERO) > 0) {
                result.add(new ExpenseSuggestionResponse(debtor.userId, nickname(users.get(debtor.userId)),
                        creditor.userId, nickname(users.get(creditor.userId)), normalize(amount)));
            }
            debtor.net = normalize(debtor.net.add(amount));
            creditor.net = normalize(creditor.net.subtract(amount));
            if (debtor.net.compareTo(ZERO) == 0) debtorIndex++;
            if (creditor.net.compareTo(ZERO) == 0) creditorIndex++;
        }
        return result;
    }

    private List<ExpenseMemberResponse> legacyMembers(Calculation calculation,
                                                       List<SettlementHistoryResponse> history,
                                                       Long currentUserId) {
        Map<Long, List<ActivityExpenseEntity>> paidBy = calculation.expenses.stream()
                .collect(Collectors.groupingBy(ActivityExpenseEntity::getPayerUserId));
        Map<Long, ActivityExpenseEntity> expenseById = calculation.expenses.stream()
                .collect(Collectors.toMap(ActivityExpenseEntity::getId, Function.identity()));
        Map<Long, List<ActivityExpenseEntity>> sharedBy = new HashMap<>();
        calculation.sharesByExpense.forEach((expenseId, expenseShares) -> {
            ActivityExpenseEntity expense = expenseById.get(expenseId);
            if (expense != null) expenseShares.forEach(share ->
                    sharedBy.computeIfAbsent(share.getUserId(), ignored -> new ArrayList<>()).add(expense));
        });
        return calculation.accounts.values().stream().map(account -> {
            UserEntity user = calculation.users.get(account.userId);
            List<SettlementHistoryResponse> related = history.stream()
                    .filter(item -> item.fromUserId().equals(account.userId) || item.toUserId().equals(account.userId))
                    .toList();
            return new ExpenseMemberResponse(account.userId, nickname(user), user == null ? null : user.getAvatarUrl(),
                    account.paid, account.share, ZERO, ZERO, account.net, settlementText(account.net),
                    listItems(paidBy.getOrDefault(account.userId, List.of()), calculation.sharesByExpense,
                            calculation.users, currentUserId),
                    listItems(sharedBy.getOrDefault(account.userId, List.of()), calculation.sharesByExpense,
                            calculation.users, currentUserId), related);
        }).toList();
    }

    private List<SettlementSuggestionResponse> legacySuggestions(List<ExpenseSuggestionResponse> suggestions,
                                                                  Long currentUserId) {
        return suggestions.stream().map(item -> new SettlementSuggestionResponse(
                item.fromUserId(), item.fromNickname(), item.toUserId(), item.toNickname(), item.amount(),
                item.fromUserId().equals(currentUserId),
                item.fromUserId().equals(currentUserId) || item.toUserId().equals(currentUserId)
        )).toList();
    }

    private List<SettlementHistoryResponse> historyRows(Long activityId) {
        List<ActivitySettlementEntity> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<ActivitySettlementEntity>()
                        .eq(ActivitySettlementEntity::getActivityId, activityId)
                        .orderByDesc(ActivitySettlementEntity::getCreateTime));
        Set<Long> userIds = new HashSet<>();
        settlements.forEach(item -> {
            userIds.add(item.getFromUserId());
            userIds.add(item.getToUserId());
        });
        Map<Long, UserEntity> users = userMap(userIds);
        return settlements.stream().map(item -> historyItem(item, users)).toList();
    }

    private List<ExpenseListItemResponse> listItems(List<ActivityExpenseEntity> expenses,
                                                    Map<Long, List<ActivityExpenseShareEntity>> shares,
                                                    Map<Long, UserEntity> users,
                                                    Long currentUserId) {
        return expenses.stream().map(item -> {
            List<ActivityExpenseShareEntity> expenseShares = shares.getOrDefault(item.getId(), List.of());
            BigDecimal currentShare = expenseShares.stream()
                    .filter(share -> share.getUserId().equals(currentUserId))
                    .map(ActivityExpenseShareEntity::getShareAmount)
                    .findFirst().orElse(null);
            return new ExpenseListItemResponse(item.getId(), item.getTitle(), item.getCategory(), money(item.getAmount()),
                    nickname(users.get(item.getPayerUserId())), item.getPayerUserId(), item.getExpenseTime(),
                    expenseShares.size(), currentShare == null ? null : money(currentShare),
                    item.getStatus(), item.getVersion());
        }).toList();
    }

    private SettlementHistoryResponse historyItem(ActivitySettlementEntity item, Map<Long, UserEntity> users) {
        return new SettlementHistoryResponse(item.getId(), item.getFromUserId(),
                nickname(users.get(item.getFromUserId())), item.getToUserId(),
                nickname(users.get(item.getToUserId())), money(item.getAmount()), item.getStatus(),
                item.getCompletedAt(), item.getCanceledAt(), item.getOperatedBy(), item.getRemark());
    }

    private Map<Long, UserEntity> userMap(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private void requireNotCanceled(ActivityEntity activity) {
        if ("CANCELED".equals(activity.getStatus())) throw new ForbiddenException("活动已取消，仅可查看历史费用");
    }

    static BigDecimal plus(BigDecimal a, BigDecimal b) { return normalize(a.add(b)); }

    static BigDecimal money(BigDecimal value) {
        if (value == null) throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "金额不能为空");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "金额最多保留两位小数");
        }
    }

    static BigDecimal normalize(BigDecimal value) {
        return value.abs().compareTo(new BigDecimal("0.005")) < 0
                ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    static String nickname(UserEntity user) {
        return user == null || user.getNickname() == null || user.getNickname().isBlank()
                ? "活动成员" : user.getNickname();
    }

    static String settlementText(BigDecimal net) {
        return net.compareTo(ZERO) > 0 ? "应收 ¥" + money(net).toPlainString()
                : net.compareTo(ZERO) < 0 ? "应付 ¥" + money(net.abs()).toPlainString()
                : "无需结算";
    }

    private static BusinessException param(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR.code(), message);
    }

    static final class Account {
        final Long userId;
        BigDecimal paid = ZERO;
        BigDecimal share = ZERO;
        BigDecimal net = ZERO;

        Account(Long userId) { this.userId = userId; }

        void finish() { net = normalize(paid.subtract(share)); }

        Account copy() {
            Account copy = new Account(userId);
            copy.net = net;
            return copy;
        }
    }

    record Calculation(List<ActivityExpenseEntity> expenses,
                       Map<Long, List<ActivityExpenseShareEntity>> sharesByExpense,
                       Map<Long, Account> accounts,
                       Map<Long, UserEntity> users,
                       List<ExpenseSuggestionResponse> suggestions,
                       BigDecimal totalExpense,
                       long financeVersion) {}
}
