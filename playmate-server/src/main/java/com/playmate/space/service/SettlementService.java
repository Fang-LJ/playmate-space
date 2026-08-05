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
    private final ActivityCollaborationAccess access;
    private final ActivityExpenseMapper expenseMapper;
    private final ActivityExpenseShareMapper shareMapper;
    private final ActivitySettlementMapper settlementMapper;
    private final ActivityMemberMapper memberMapper;
    private final UserMapper userMapper;

    public SettlementService(ActivityCollaborationAccess access, ActivityExpenseMapper expenseMapper,
                             ActivityExpenseShareMapper shareMapper, ActivitySettlementMapper settlementMapper,
                             ActivityMemberMapper memberMapper, UserMapper userMapper) {
        this.access = access; this.expenseMapper = expenseMapper; this.shareMapper = shareMapper;
        this.settlementMapper = settlementMapper; this.memberMapper = memberMapper; this.userMapper = userMapper;
    }

    public ExpenseSummaryResponse expenseSummary(Long activityId) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        return toExpenseSummary(calculate(activityId, userId));
    }

    public SettlementSummaryResponse summary(Long activityId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        Calculation calculation = calculate(activityId, userId);
        return new SettlementSummaryResponse(toExpenseSummary(calculation), calculation.suggestions, calculation.history,
                calculation.members, calculation.totalExpense, calculation.expenses.size(),
                "剩余净额 = 实际付款 - 应承担 + 已转出 - 已收到；建议按稳定贪心匹配生成。");
    }

    public List<ExpenseMemberResponse> members(Long activityId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        return calculate(activityId, userId).members;
    }

    public List<SettlementHistoryResponse> history(Long activityId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        return calculate(activityId, userId).history;
    }

    /** Used before removing a member: historical expenses must be settled first. */
    public BigDecimal remainingNet(Long activityId, Long userId) {
        return calculate(activityId, userId).accounts.getOrDefault(userId, new Account(userId)).net;
    }

    @Transactional
    public SettlementHistoryResponse complete(Long activityId, CompleteSettlementRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        if (request.fromUserId().equals(request.toUserId())) throw param("付款人与收款人不能相同");
        BigDecimal amount = money(request.amount());
        Calculation calculation = calculate(activityId, userId);
        boolean valid = calculation.suggestions.stream().anyMatch(item -> item.fromUserId().equals(request.fromUserId())
                && item.toUserId().equals(request.toUserId()) && item.amount().compareTo(amount) == 0);
        if (!valid) throw new BusinessException(ErrorCode.BUSINESS_ERROR.code(), "当前结算建议已变化，请刷新后重试");
        boolean creator = access.isActivityCreator(activity, member, userId);
        if (!creator && !userId.equals(request.fromUserId())) throw new ForbiddenException("仅付款人可以标记已转账");
        LocalDateTime now = LocalDateTime.now();
        ActivitySettlementEntity entity = new ActivitySettlementEntity();
        entity.setActivityId(activityId); entity.setFromUserId(request.fromUserId()); entity.setToUserId(request.toUserId()); entity.setAmount(amount);
        entity.setStatus("COMPLETED"); entity.setCompletedAt(now); entity.setOperatedBy(userId); entity.setRemark(trim(request.remark()));
        entity.setCreateTime(now); entity.setUpdateTime(now); entity.setDeleteFlag(0); settlementMapper.insert(entity);
        return historyItem(entity, userMap(Set.of(entity.getFromUserId(), entity.getToUserId())));
    }

    @Transactional
    public SettlementHistoryResponse cancel(Long activityId, Long settlementId, CancelSettlementRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity member = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        ActivitySettlementEntity entity = settlementMapper.selectById(settlementId);
        if (entity == null || !activityId.equals(entity.getActivityId())) throw new NotFoundException("转账记录不存在");
        if (!"COMPLETED".equals(entity.getStatus())) throw param("仅已完成转账可以撤销");
        if (!userId.equals(entity.getOperatedBy()) && !access.isActivityCreator(activity, member, userId)) throw new ForbiddenException("无权撤销该转账记录");
        entity.setStatus("CANCELED"); entity.setCanceledAt(LocalDateTime.now()); entity.setCanceledBy(userId); entity.setCancelReason(trim(request == null ? null : request.reason())); entity.setUpdateTime(LocalDateTime.now()); settlementMapper.updateById(entity);
        return historyItem(entity, userMap(Set.of(entity.getFromUserId(), entity.getToUserId())));
    }

    Calculation calculate(Long activityId, Long currentUserId) {
        List<ActivityExpenseEntity> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ActivityExpenseEntity>()
                .eq(ActivityExpenseEntity::getActivityId, activityId).eq(ActivityExpenseEntity::getStatus, "ACTIVE")
                .orderByDesc(ActivityExpenseEntity::getExpenseTime).orderByDesc(ActivityExpenseEntity::getId));
        Set<Long> expenseIds = expenses.stream().map(ActivityExpenseEntity::getId).collect(Collectors.toSet());
        List<ActivityExpenseShareEntity> shares = expenseIds.isEmpty() ? List.of() : shareMapper.selectList(new LambdaQueryWrapper<ActivityExpenseShareEntity>().in(ActivityExpenseShareEntity::getExpenseId, expenseIds));
        List<ActivitySettlementEntity> settlements = settlementMapper.selectList(new LambdaQueryWrapper<ActivitySettlementEntity>()
                .eq(ActivitySettlementEntity::getActivityId, activityId).orderByDesc(ActivitySettlementEntity::getCreateTime));
        List<ActivityMemberEntity> activeMembers = memberMapper.selectList(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId).eq(ActivityMemberEntity::getMemberStatus, "ACTIVE"));

        Set<Long> userIds = new HashSet<>();
        activeMembers.forEach(item -> userIds.add(item.getUserId()));
        expenses.forEach(item -> { userIds.add(item.getPayerUserId()); userIds.add(item.getCreatedBy()); });
        shares.forEach(item -> userIds.add(item.getUserId()));
        settlements.forEach(item -> { userIds.add(item.getFromUserId()); userIds.add(item.getToUserId()); });
        Map<Long, UserEntity> users = userMap(userIds);
        Map<Long, Account> accounts = new TreeMap<>();
        userIds.forEach(id -> accounts.put(id, new Account(id)));
        expenses.forEach(item -> accounts.get(item.getPayerUserId()).paid = plus(accounts.get(item.getPayerUserId()).paid, item.getAmount()));
        shares.forEach(item -> accounts.get(item.getUserId()).share = plus(accounts.get(item.getUserId()).share, item.getShareAmount()));
        settlements.stream().filter(item -> "COMPLETED".equals(item.getStatus())).forEach(item -> {
            accounts.get(item.getFromUserId()).out = plus(accounts.get(item.getFromUserId()).out, item.getAmount());
            accounts.get(item.getToUserId()).received = plus(accounts.get(item.getToUserId()).received, item.getAmount());
        });
        accounts.values().forEach(Account::finish);
        List<SettlementSuggestionResponse> suggestions = suggestions(accounts, users, currentUserId);
        List<SettlementHistoryResponse> history = settlements.stream().map(item -> historyItem(item, users)).toList();
        Map<Long, List<ActivityExpenseShareEntity>> sharesByExpense = shares.stream().collect(Collectors.groupingBy(ActivityExpenseShareEntity::getExpenseId));
        Map<Long, List<ActivityExpenseEntity>> paidBy = expenses.stream().collect(Collectors.groupingBy(ActivityExpenseEntity::getPayerUserId));
        Map<Long, List<ActivityExpenseEntity>> sharedBy = new HashMap<>();
        for (ActivityExpenseShareEntity share : shares) {
            ActivityExpenseEntity expense = expenses.stream().filter(item -> item.getId().equals(share.getExpenseId())).findFirst().orElse(null);
            if (expense != null) sharedBy.computeIfAbsent(share.getUserId(), ignored -> new ArrayList<>()).add(expense);
        }
        List<ExpenseMemberResponse> memberResponses = accounts.values().stream().map(account -> {
            UserEntity user = users.get(account.userId);
            List<SettlementHistoryResponse> related = history.stream().filter(item -> item.fromUserId().equals(account.userId) || item.toUserId().equals(account.userId)).toList();
            return new ExpenseMemberResponse(account.userId, nickname(user), user == null ? null : user.getAvatarUrl(), account.paid, account.share, account.out, account.received,
                    account.net, settlementText(account.net), listItems(paidBy.getOrDefault(account.userId, List.of()), sharesByExpense, users, currentUserId),
                    listItems(sharedBy.getOrDefault(account.userId, List.of()), sharesByExpense, users, currentUserId), related);
        }).toList();
        return new Calculation(expenses, sharesByExpense, accounts, users, suggestions, history, memberResponses,
                expenses.stream().map(ActivityExpenseEntity::getAmount).reduce(ZERO, SettlementService::plus));
    }

    private ExpenseSummaryResponse toExpenseSummary(Calculation calc) {
        Long current = access.requireUserId();
        Account account = calc.accounts.getOrDefault(current, new Account(current)); account.finish();
        List<SettlementSuggestionResponse> mine = calc.suggestions.stream().filter(item -> item.currentUserRelated()).toList();
        BigDecimal pay = mine.stream().filter(item -> item.fromUserId().equals(current)).map(SettlementSuggestionResponse::amount).reduce(ZERO, SettlementService::plus);
        BigDecimal receive = mine.stream().filter(item -> item.toUserId().equals(current)).map(SettlementSuggestionResponse::amount).reduce(ZERO, SettlementService::plus);
        int completed = (int) calc.history.stream().filter(item -> "COMPLETED".equals(item.status())).count();
        BigDecimal completedAmount = calc.history.stream().filter(item -> "COMPLETED".equals(item.status())).map(SettlementHistoryResponse::amount).reduce(ZERO, SettlementService::plus);
        return new ExpenseSummaryResponse(pay, receive, normalize(receive.subtract(pay)), settlementText(normalize(receive.subtract(pay))), mine,
                calc.suggestions.size(), completed, completedAmount, calc.totalExpense, calc.expenses.size(),
                listItems(calc.expenses.stream().limit(2).toList(), calc.sharesByExpense, calc.users, current));
    }

    private List<SettlementSuggestionResponse> suggestions(Map<Long, Account> accounts, Map<Long, UserEntity> users, Long currentUserId) {
        List<Account> debtors = accounts.values().stream().filter(item -> item.net.compareTo(ZERO) < 0).sorted(Comparator.comparing(item -> item.userId)).map(Account::copy).toList();
        List<Account> creditors = accounts.values().stream().filter(item -> item.net.compareTo(ZERO) > 0).sorted(Comparator.comparing(item -> item.userId)).map(Account::copy).toList();
        List<SettlementSuggestionResponse> result = new ArrayList<>(); int debtorIndex = 0, creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Account debtor = debtors.get(debtorIndex), creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.net.abs().min(creditor.net);
            if (amount.compareTo(ZERO) > 0) result.add(new SettlementSuggestionResponse(debtor.userId, nickname(users.get(debtor.userId)), creditor.userId,
                    nickname(users.get(creditor.userId)), normalize(amount), debtor.userId.equals(currentUserId), debtor.userId.equals(currentUserId) || creditor.userId.equals(currentUserId)));
            debtor.net = normalize(debtor.net.add(amount)); creditor.net = normalize(creditor.net.subtract(amount));
            if (debtor.net.compareTo(ZERO) == 0) debtorIndex++; if (creditor.net.compareTo(ZERO) == 0) creditorIndex++;
        }
        return result;
    }

    private List<ExpenseListItemResponse> listItems(List<ActivityExpenseEntity> expenses, Map<Long, List<ActivityExpenseShareEntity>> shares, Map<Long, UserEntity> users, Long currentUserId) {
        return expenses.stream().map(item -> {
            List<ActivityExpenseShareEntity> expenseShares = shares.getOrDefault(item.getId(), List.of());
            BigDecimal currentShare = expenseShares.stream().filter(share -> share.getUserId().equals(currentUserId)).map(ActivityExpenseShareEntity::getShareAmount).findFirst().orElse(null);
            return new ExpenseListItemResponse(item.getId(), item.getTitle(), item.getCategory(), money(item.getAmount()), nickname(users.get(item.getPayerUserId())),
                    item.getPayerUserId(), item.getExpenseTime(), expenseShares.size(), currentShare == null ? null : money(currentShare), item.getStatus(), item.getVersion());
        }).toList();
    }

    private SettlementHistoryResponse historyItem(ActivitySettlementEntity item, Map<Long, UserEntity> users) {
        return new SettlementHistoryResponse(item.getId(), item.getFromUserId(), nickname(users.get(item.getFromUserId())), item.getToUserId(), nickname(users.get(item.getToUserId())),
                money(item.getAmount()), item.getStatus(), item.getCompletedAt(), item.getCanceledAt(), item.getOperatedBy(), item.getRemark());
    }
    private Map<Long, UserEntity> userMap(Set<Long> ids) { return ids.isEmpty() ? Map.of() : userMapper.selectByIds(ids).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity())); }
    private void requireNotCanceled(ActivityEntity activity) { if ("CANCELED".equals(activity.getStatus())) throw new ForbiddenException("活动已取消，仅可查看历史费用"); }
    static BigDecimal plus(BigDecimal a, BigDecimal b){ return normalize(a.add(b)); }
    static BigDecimal money(BigDecimal value){ if(value==null) throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "金额不能为空"); try{return value.setScale(2, RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "金额最多保留两位小数");} }
    static BigDecimal normalize(BigDecimal value){ return value.abs().compareTo(new BigDecimal("0.005")) < 0 ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    static String trim(String value){ return value == null || value.trim().isEmpty() ? null : value.trim(); }
    static String nickname(UserEntity user){ return user == null || user.getNickname() == null || user.getNickname().isBlank() ? "活动成员" : user.getNickname(); }
    static String settlementText(BigDecimal net){ return net.compareTo(ZERO)>0 ? "应收 " + money(net).toPlainString() : net.compareTo(ZERO)<0 ? "应付 " + money(net.abs()).toPlainString() : "已结清"; }
    private static BusinessException param(String message){ return new BusinessException(ErrorCode.PARAM_ERROR.code(), message); }
    static final class Account { final Long userId; BigDecimal paid=ZERO, share=ZERO, out=ZERO, received=ZERO, net=ZERO; Account(Long id){userId=id;} void finish(){net=normalize(paid.subtract(share).add(out).subtract(received));} Account copy(){Account r=new Account(userId);r.net=net;return r;} }
    record Calculation(List<ActivityExpenseEntity> expenses, Map<Long,List<ActivityExpenseShareEntity>> sharesByExpense, Map<Long,Account> accounts, Map<Long,UserEntity> users,
                       List<SettlementSuggestionResponse> suggestions, List<SettlementHistoryResponse> history, List<ExpenseMemberResponse> members, BigDecimal totalExpense) {}
}
