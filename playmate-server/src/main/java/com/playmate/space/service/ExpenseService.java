package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {
    private static final Set<String> CATEGORIES = Set.of("TRANSPORT", "LODGING", "TICKET", "FOOD", "ENTERTAINMENT", "SHOPPING", "OTHER");
    private static final Set<String> SPLIT_MODES = Set.of("EQUAL", "CUSTOM");
    private final ActivityCollaborationAccess access;
    private final ActivityExpenseMapper expenseMapper;
    private final ActivityExpenseShareMapper shareMapper;
    private final ActivityMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final FileMapper fileMapper;
    private final SettlementService settlementService;

    public ExpenseService(ActivityCollaborationAccess access, ActivityExpenseMapper expenseMapper, ActivityExpenseShareMapper shareMapper,
                          ActivityMemberMapper memberMapper, UserMapper userMapper, FileMapper fileMapper, SettlementService settlementService) {
        this.access = access; this.expenseMapper = expenseMapper; this.shareMapper = shareMapper; this.memberMapper = memberMapper;
        this.userMapper = userMapper; this.fileMapper = fileMapper; this.settlementService = settlementService;
    }

    public List<ExpenseListItemResponse> list(Long activityId, String category, Integer page, Integer pageSize, String sort) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        LambdaQueryWrapper<ActivityExpenseEntity> query = new LambdaQueryWrapper<ActivityExpenseEntity>().eq(ActivityExpenseEntity::getActivityId, activityId)
                .orderByDesc(ActivityExpenseEntity::getExpenseTime).orderByDesc(ActivityExpenseEntity::getId);
        if (category != null && !category.isBlank()) query.eq(ActivityExpenseEntity::getCategory, category.trim().toUpperCase());
        if (sort != null && !sort.isBlank() && !"expenseTimeDesc".equals(sort)) throw param("仅支持 expenseTimeDesc 排序");
        long current = page == null ? 1 : page;
        long size = pageSize == null ? 50 : pageSize;
        if (current < 1 || size < 1 || size > 100) throw param("分页参数不合法");
        return toListItems(expenseMapper.selectPage(new Page<>(current, size, false), query).getRecords(), userId);
    }

    public ExpenseDetailResponse detail(Long activityId, Long expenseId) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        return toDetail(find(activityId, expenseId));
    }

    @Transactional
    public ExpenseDetailResponse create(Long activityId, SaveExpenseRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity operator = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity); validateRequest(activityId, request, userId, access.isActivityCreator(activity, operator, userId));
        LocalDateTime now = LocalDateTime.now();
        ActivityExpenseEntity expense = new ActivityExpenseEntity(); apply(expense, request); expense.setActivityId(activityId); expense.setCreatedBy(userId); expense.setStatus("ACTIVE");
        expense.setVersion(1); expense.setCreateTime(now); expense.setUpdateTime(now); expense.setDeleteFlag(0); expenseMapper.insert(expense);
        replaceShares(expense.getId(), request, now); return toDetail(expense);
    }

    @Transactional
    public ExpenseDetailResponse update(Long activityId, Long expenseId, SaveExpenseRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity operator = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity); ActivityExpenseEntity expense = find(activityId, expenseId); requireEditable(expense, activity, operator, userId);
        if (request.version() == null || !request.version().equals(expense.getVersion())) throw param("账单已被其他成员修改，请刷新后重试");
        validateRequest(activityId, request, userId, access.isActivityCreator(activity, operator, userId)); apply(expense, request); expense.setVersion(expense.getVersion() + 1); expense.setUpdateTime(LocalDateTime.now()); expenseMapper.updateById(expense);
        replaceShares(expenseId, request, expense.getUpdateTime()); return toDetail(expense);
    }

    @Transactional
    public ExpenseDetailResponse voidExpense(Long activityId, Long expenseId, VoidExpenseRequest request) {
        Long userId = access.requireUserId(); ActivityEntity activity = access.requireActivity(activityId); ActivityMemberEntity operator = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity); ActivityExpenseEntity expense = find(activityId, expenseId); requireEditable(expense, activity, operator, userId);
        if ("VOID".equals(expense.getStatus())) return toDetail(expense);
        expense.setStatus("VOID"); expense.setVoidedBy(userId); expense.setVoidedAt(LocalDateTime.now()); expense.setVoidReason(SettlementService.trim(request == null ? null : request.reason())); expense.setVersion(expense.getVersion() + 1); expense.setUpdateTime(LocalDateTime.now()); expenseMapper.updateById(expense);
        return toDetail(expense);
    }

    private void validateRequest(Long activityId, SaveExpenseRequest request, Long userId, boolean creator) {
        if (!CATEGORIES.contains(request.category().trim().toUpperCase())) throw param("费用分类不支持");
        if (!SPLIT_MODES.contains(request.splitMode().trim().toUpperCase())) throw param("分摊方式只支持 EQUAL 或 CUSTOM");
        SettlementService.money(request.amount());
        access.requireActiveMember(activityId, request.payerUserId());
        if (!creator && !userId.equals(request.payerUserId())) throw new ForbiddenException("普通成员只能记录自己支付的账单");
        if (request.receiptFileId() != null) validateReceipt(request.receiptFileId(), userId);
        Set<Long> users = request.shares().stream().map(ExpenseShareRequest::userId).collect(Collectors.toSet());
        if (users.size() != request.shares().size()) throw param("分摊成员不能重复");
        users.forEach(memberId -> access.requireActiveMember(activityId, memberId));
        if ("CUSTOM".equals(request.splitMode().trim().toUpperCase())) {
            BigDecimal total = request.shares().stream().map(ExpenseShareRequest::shareAmount).filter(Objects::nonNull).map(SettlementService::money).reduce(BigDecimal.ZERO, SettlementService::plus);
            if (total.compareTo(SettlementService.money(request.amount())) != 0) throw param("自定义分摊金额之和必须等于总金额");
        }
    }
    private void validateReceipt(Long fileId, Long userId) { FileEntity file = fileMapper.selectById(fileId); if (file == null || !"EXPENSE_RECEIPT".equals(file.getFileType()) || !userId.equals(file.getUploadUserId())) throw param("付款凭证无效或不属于当前用户"); }
    private void apply(ActivityExpenseEntity expense, SaveExpenseRequest request) { expense.setTitle(request.title().trim()); expense.setCategory(request.category().trim().toUpperCase()); expense.setAmount(SettlementService.money(request.amount())); expense.setPayerUserId(request.payerUserId()); expense.setSplitMode(request.splitMode().trim().toUpperCase()); expense.setExpenseTime(request.expenseTime()); expense.setReceiptFileId(request.receiptFileId()); expense.setDescription(SettlementService.trim(request.description())); }
    private void replaceShares(Long expenseId, SaveExpenseRequest request, LocalDateTime now) {
        shareMapper.deleteByExpenseId(expenseId); List<ActivityExpenseShareEntity> shares = buildShares(expenseId, request, now); for (ActivityExpenseShareEntity share : shares) shareMapper.insert(share);
    }
    private List<ActivityExpenseShareEntity> buildShares(Long expenseId, SaveExpenseRequest request, LocalDateTime now) {
        List<ExpenseShareRequest> requests = request.shares().stream().sorted(Comparator.comparing(ExpenseShareRequest::userId)).toList(); BigDecimal amount = SettlementService.money(request.amount());
        long cents = amount.movePointRight(2).longValueExact(); long base = cents / requests.size(), remainder = cents % requests.size(); List<ActivityExpenseShareEntity> result = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) { ExpenseShareRequest item = requests.get(index); BigDecimal share = "EQUAL".equals(request.splitMode().trim().toUpperCase()) ? BigDecimal.valueOf(base + (index < remainder ? 1 : 0), 2) : SettlementService.money(item.shareAmount());
            if (share.signum() < 0) throw param("分摊金额不能小于零"); ActivityExpenseShareEntity entity = new ActivityExpenseShareEntity(); entity.setExpenseId(expenseId); entity.setUserId(item.userId()); entity.setShareAmount(share); entity.setCreateTime(now); entity.setUpdateTime(now); entity.setDeleteFlag(0); result.add(entity); }
        return result;
    }
    private ActivityExpenseEntity find(Long activityId, Long expenseId) { ActivityExpenseEntity expense = expenseMapper.selectById(expenseId); if (expense == null || !activityId.equals(expense.getActivityId())) throw new NotFoundException("账单不存在"); return expense; }
    private void requireEditable(ActivityExpenseEntity expense, ActivityEntity activity, ActivityMemberEntity operator, Long userId) { if (!"ACTIVE".equals(expense.getStatus())) throw param("已作废账单不能编辑"); if (!userId.equals(expense.getCreatedBy()) && !access.isActivityCreator(activity, operator, userId)) throw new ForbiddenException("仅记录人或活动创建者可以操作账单"); }
    private void requireNotCanceled(ActivityEntity activity) { if ("CANCELED".equals(activity.getStatus())) throw new ForbiddenException("活动已取消，仅可查看历史费用"); }
    private ExpenseDetailResponse toDetail(ActivityExpenseEntity expense) {
        List<ActivityExpenseShareEntity> shares = shareMapper.selectList(new LambdaQueryWrapper<ActivityExpenseShareEntity>().eq(ActivityExpenseShareEntity::getExpenseId, expense.getId())); Set<Long> ids = new HashSet<>(); ids.add(expense.getPayerUserId()); ids.add(expense.getCreatedBy()); shares.forEach(item -> ids.add(item.getUserId())); Map<Long, UserEntity> users = ids.isEmpty() ? Map.of() : userMapper.selectByIds(ids).stream().collect(Collectors.toMap(UserEntity::getId, item -> item)); FileEntity receipt = expense.getReceiptFileId() == null ? null : fileMapper.selectById(expense.getReceiptFileId());
        return new ExpenseDetailResponse(expense.getId(), expense.getActivityId(), expense.getTitle(), expense.getCategory(), expense.getAmount(), expense.getPayerUserId(), SettlementService.nickname(users.get(expense.getPayerUserId())), expense.getCreatedBy(), SettlementService.nickname(users.get(expense.getCreatedBy())), expense.getExpenseTime(), expense.getSplitMode(), expense.getReceiptFileId(), receipt == null ? null : receipt.getUrl(), expense.getDescription(), expense.getStatus(), expense.getVoidReason(), expense.getVersion(), shares.stream().map(item -> new ExpenseShareResponse(item.getUserId(), SettlementService.nickname(users.get(item.getUserId())), users.get(item.getUserId()) == null ? null : users.get(item.getUserId()).getAvatarUrl(), item.getShareAmount())).toList(), expense.getCreateTime(), expense.getUpdateTime());
    }
    private List<ExpenseListItemResponse> toListItems(List<ActivityExpenseEntity> expenses, Long currentUserId) { if (expenses.isEmpty()) return List.of(); Set<Long> ids = expenses.stream().map(ActivityExpenseEntity::getId).collect(Collectors.toSet()); Map<Long,List<ActivityExpenseShareEntity>> shares = shareMapper.selectList(new LambdaQueryWrapper<ActivityExpenseShareEntity>().in(ActivityExpenseShareEntity::getExpenseId, ids)).stream().collect(Collectors.groupingBy(ActivityExpenseShareEntity::getExpenseId)); Set<Long> payerIds = expenses.stream().map(ActivityExpenseEntity::getPayerUserId).collect(Collectors.toSet()); Map<Long,UserEntity> users = userMapper.selectByIds(payerIds).stream().collect(Collectors.toMap(UserEntity::getId, item -> item)); return expenses.stream().map(item -> { List<ActivityExpenseShareEntity> lines = shares.getOrDefault(item.getId(), List.of()); BigDecimal currentShare = lines.stream().filter(line -> currentUserId.equals(line.getUserId())).map(ActivityExpenseShareEntity::getShareAmount).findFirst().orElse(null); return new ExpenseListItemResponse(item.getId(), item.getTitle(), item.getCategory(), item.getAmount(), SettlementService.nickname(users.get(item.getPayerUserId())), item.getPayerUserId(), item.getExpenseTime(), lines.size(), currentShare, item.getStatus(), item.getVersion()); }).toList(); }
    private static BusinessException param(String message) { return new BusinessException(ErrorCode.PARAM_ERROR.code(), message); }
}
