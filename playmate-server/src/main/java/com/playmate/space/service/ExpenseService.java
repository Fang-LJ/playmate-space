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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {
    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    private static final Set<String> CATEGORIES = Set.of("TRANSPORT", "LODGING", "TICKET", "FOOD", "ENTERTAINMENT", "SHOPPING", "OTHER");
    private static final Set<String> SPLIT_MODES = Set.of("EQUAL", "CUSTOM", "PROPORTIONAL");
    private static final BigDecimal DEFAULT_SPLIT_RATIO = new BigDecimal("1.0000");
    private final ActivityCollaborationAccess access;
    private final ActivityExpenseMapper expenseMapper;
    private final ActivityExpenseShareMapper shareMapper;
    private final ActivityMemberDisplayService memberDisplayService;
    private final FileMapper fileMapper;
    private final SettlementService settlementService;
    private final ActivityFinanceStateService financeStateService;

    public ExpenseService(ActivityCollaborationAccess access, ActivityExpenseMapper expenseMapper, ActivityExpenseShareMapper shareMapper,
                          ActivityMemberDisplayService memberDisplayService, FileMapper fileMapper, SettlementService settlementService,
                          ActivityFinanceStateService financeStateService) {
        this.access = access; this.expenseMapper = expenseMapper; this.shareMapper = shareMapper; this.memberDisplayService = memberDisplayService;
        this.fileMapper = fileMapper; this.settlementService = settlementService;
        this.financeStateService = financeStateService;
    }

    public List<ExpenseListItemResponse> list(Long activityId, String category, Integer page, Integer pageSize, String sort) {
        Long userId = access.requireUserId(); access.requireActivity(activityId); access.requireActiveMember(activityId, userId);
        LambdaQueryWrapper<ActivityExpenseEntity> query = new LambdaQueryWrapper<ActivityExpenseEntity>().eq(ActivityExpenseEntity::getActivityId, activityId)
                .eq(ActivityExpenseEntity::getStatus, "ACTIVE")
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
        Long userId = access.requireUserId();
        String clientRequestId = requireClientRequestId(request.clientRequestId());
        access.requireActivity(activityId);
        financeStateService.ensureAndLock(activityId);
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity operator = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        ActivityExpenseEntity existing = expenseMapper.selectByCreateRequest(activityId, userId, clientRequestId);
        if (existing != null) {
            log.warn("Duplicate expense create request returns existing expense: activityId={}, userId={}, clientRequestId={}, expenseId={}",
                    activityId, userId, clientRequestId, existing.getId());
            return toDetailAfterLock(existing);
        }
        validateRequest(activityId, request, userId, access.isActivityCreator(activity, operator, userId));
        validateReceiptChange(null, request.receiptFileId(), userId);
        LocalDateTime now = LocalDateTime.now();
        ActivityExpenseEntity expense = new ActivityExpenseEntity(); apply(expense, request); expense.setActivityId(activityId); expense.setCreatedBy(userId); expense.setStatus("ACTIVE");
        expense.setClientRequestId(clientRequestId); expense.setVersion(1); expense.setCreateTime(now); expense.setUpdateTime(now); expense.setDeleteFlag(0);
        try {
            expenseMapper.insert(expense);
        } catch (DuplicateKeyException duplicate) {
            ActivityExpenseEntity duplicated = expenseMapper.selectByCreateRequest(activityId, userId, clientRequestId);
            if (duplicated != null) {
                log.warn("Concurrent duplicate expense request returns existing expense: activityId={}, userId={}, clientRequestId={}, expenseId={}",
                        activityId, userId, clientRequestId, duplicated.getId());
                return toDetailAfterLock(duplicated);
            }
            throw duplicate;
        }
        replaceShares(expense.getId(), request, now);
        financeStateService.incrementVersion(activityId);
        return toDetail(expense);
    }

    @Transactional
    public ExpenseDetailResponse update(Long activityId, Long expenseId, SaveExpenseRequest request) {
        Long userId = access.requireUserId();
        access.requireActivity(activityId);
        financeStateService.ensureAndLock(activityId);
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity operator = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        ActivityExpenseEntity expense = findForUpdate(activityId, expenseId); requireEditable(expense, activity, operator, userId);
        if (request.version() == null) throw param("编辑账单时必须提供版本号");
        if (!Objects.equals(expense.getVersion(), request.version())) throw versionConflict();
        validateRequest(activityId, request, userId, access.isActivityCreator(activity, operator, userId));
        validateReceiptChange(expense.getReceiptFileId(), request.receiptFileId(), userId);
        List<ActivityExpenseShareEntity> currentShares = loadShares(expenseId);
        if (!hasChanges(expense, currentShares, request)) return toDetail(expense);
        apply(expense, request);
        LocalDateTime updateTime = LocalDateTime.now();
        int affectedRows = expenseMapper.updateActiveByVersion(expense, request.version(), updateTime);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR.code(), "账单已被其他成员修改，请刷新后重试");
        }
        expense.setVersion(request.version() + 1);
        expense.setUpdateTime(updateTime);
        replaceShares(expenseId, request, updateTime);
        financeStateService.incrementVersion(activityId);
        return toDetail(expense);
    }

    @Transactional
    public ExpenseDetailResponse voidExpense(Long activityId, Long expenseId, VoidExpenseRequest request) {
        Long userId = access.requireUserId();
        if (request == null || request.expectedVersion() == null) throw param("删除账单时必须提供版本号");
        access.requireActivity(activityId);
        financeStateService.ensureAndLock(activityId);
        ActivityEntity activity = access.requireActivity(activityId);
        ActivityMemberEntity operator = access.requireActiveMember(activityId, userId);
        requireNotCanceled(activity);
        ActivityExpenseEntity expense = findForUpdate(activityId, expenseId);
        requireExpenseOperator(expense, activity, operator, userId);
        LocalDateTime now = LocalDateTime.now();
        int affectedRows = expenseMapper.voidActiveByVersion(activityId, expenseId, request.expectedVersion(), userId,
                now, SettlementService.trim(request.reason()), now);
        if (affectedRows != 1) throw versionConflict();
        expense.setStatus("VOID"); expense.setVoidedBy(userId); expense.setVoidedAt(now);
        expense.setVoidReason(SettlementService.trim(request.reason())); expense.setVersion(request.expectedVersion() + 1); expense.setUpdateTime(now);
        financeStateService.incrementVersion(activityId);
        return toDetail(expense);
    }

    private void validateRequest(Long activityId, SaveExpenseRequest request, Long userId, boolean creator) {
        if (!CATEGORIES.contains(request.category().trim().toUpperCase())) throw param("费用分类不支持");
        String splitMode = request.splitMode().trim().toUpperCase();
        if (!SPLIT_MODES.contains(splitMode)) throw param("分摊方式只支持 EQUAL、CUSTOM 或 PROPORTIONAL");
        SettlementService.money(request.amount());
        access.requireActiveMember(activityId, request.payerUserId());
        if (!creator && !userId.equals(request.payerUserId())) throw new ForbiddenException("普通成员只能记录自己支付的账单");
        Set<Long> users = request.shares().stream().map(ExpenseShareRequest::userId).collect(Collectors.toSet());
        if (users.size() != request.shares().size()) throw param("分摊成员不能重复");
        users.forEach(memberId -> access.requireActiveMember(activityId, memberId));
        if ("CUSTOM".equals(splitMode)) {
            BigDecimal total = BigDecimal.ZERO;
            for (ExpenseShareRequest share : request.shares()) {
                if (share.shareAmount() == null) throw param("自定义分摊金额不能为空");
                BigDecimal shareAmount = SettlementService.money(share.shareAmount());
                if (shareAmount.signum() < 0) throw param("分摊金额不能小于零");
                total = SettlementService.plus(total, shareAmount);
            }
            if (total.compareTo(SettlementService.money(request.amount())) != 0) throw param("自定义分摊金额之和必须等于总金额");
        }
        if ("PROPORTIONAL".equals(splitMode)) {
            BigDecimal totalRatio = BigDecimal.ZERO;
            for (ExpenseShareRequest share : request.shares()) {
                BigDecimal ratio = share.splitRatio();
                if (ratio == null || ratio.signum() <= 0) throw param("分摊比例必须大于零");
                if (ratio.scale() > 4) throw param("分摊比例最多保留四位小数");
                totalRatio = totalRatio.add(ratio);
            }
            if (totalRatio.signum() <= 0) throw param("分摊比例必须大于零");
        }
    }
    private void validateReceiptChange(Long oldReceiptFileId, Long newReceiptFileId, Long userId) {
        if (Objects.equals(oldReceiptFileId, newReceiptFileId) || newReceiptFileId == null) return;
        FileEntity file = fileMapper.selectById(newReceiptFileId);
        if (file == null || !"EXPENSE_RECEIPT".equals(file.getFileType()) || !"NORMAL".equals(file.getStatus())
                || !Integer.valueOf(0).equals(file.getDeleteFlag()) || !userId.equals(file.getUploadUserId())) {
            throw param("付款凭证无效或不属于当前用户");
        }
    }
    private void apply(ActivityExpenseEntity expense, SaveExpenseRequest request) { expense.setTitle(request.title().trim()); expense.setCategory(request.category().trim().toUpperCase()); expense.setAmount(SettlementService.money(request.amount())); expense.setPayerUserId(request.payerUserId()); expense.setSplitMode(request.splitMode().trim().toUpperCase()); expense.setExpenseTime(request.expenseTime()); expense.setReceiptFileId(request.receiptFileId()); expense.setDescription(SettlementService.trim(request.description())); }
    private void replaceShares(Long expenseId, SaveExpenseRequest request, LocalDateTime now) {
        shareMapper.deleteByExpenseId(expenseId); List<ActivityExpenseShareEntity> shares = buildShares(expenseId, request, now); for (ActivityExpenseShareEntity share : shares) shareMapper.insert(share);
    }
    private List<ActivityExpenseShareEntity> loadShares(Long expenseId) {
        return shareMapper.selectByExpenseIdForUpdate(expenseId);
    }
    private boolean hasChanges(ActivityExpenseEntity expense, List<ActivityExpenseShareEntity> currentShares, SaveExpenseRequest request) {
        if (!Objects.equals(expense.getTitle(), request.title().trim())
                || !Objects.equals(expense.getCategory(), request.category().trim().toUpperCase())
                || expense.getAmount().compareTo(SettlementService.money(request.amount())) != 0
                || !Objects.equals(expense.getPayerUserId(), request.payerUserId())
                || !Objects.equals(expense.getSplitMode(), request.splitMode().trim().toUpperCase())
                || !Objects.equals(expense.getExpenseTime(), request.expenseTime())
                || !Objects.equals(expense.getReceiptFileId(), request.receiptFileId())
                || !Objects.equals(expense.getDescription(), SettlementService.trim(request.description()))) return true;
        Map<Long, ShareValue> before = currentShares.stream().collect(Collectors.toMap(
                ActivityExpenseShareEntity::getUserId, item -> new ShareValue(item.getShareAmount(), storedRatio(item))));
        Map<Long, ShareValue> after = buildShares(expense.getId(), request, LocalDateTime.now()).stream().collect(Collectors.toMap(
                ActivityExpenseShareEntity::getUserId, item -> new ShareValue(item.getShareAmount(), storedRatio(item))));
        return !before.equals(after);
    }
    private List<ActivityExpenseShareEntity> buildShares(Long expenseId, SaveExpenseRequest request, LocalDateTime now) {
        List<ExpenseShareRequest> requests = request.shares().stream().sorted(Comparator.comparing(ExpenseShareRequest::userId)).toList();
        BigDecimal amount = SettlementService.money(request.amount());
        String splitMode = request.splitMode().trim().toUpperCase();
        Map<Long, BigDecimal> shares = switch (splitMode) {
            case "EQUAL" -> equalShares(amount, requests);
            case "PROPORTIONAL" -> proportionalShares(amount, requests);
            default -> requests.stream().collect(Collectors.toMap(ExpenseShareRequest::userId,
                    item -> SettlementService.money(item.shareAmount()), (first, second) -> first, LinkedHashMap::new));
        };
        List<ActivityExpenseShareEntity> result = new ArrayList<>();
        for (ExpenseShareRequest item : requests) {
            BigDecimal share = shares.get(item.userId());
            if (share.signum() < 0) throw param("分摊金额不能小于零");
            ActivityExpenseShareEntity entity = new ActivityExpenseShareEntity();
            entity.setExpenseId(expenseId); entity.setUserId(item.userId()); entity.setShareAmount(share);
            entity.setSplitRatio("PROPORTIONAL".equals(splitMode) ? item.splitRatio().setScale(4, RoundingMode.UNNECESSARY) : DEFAULT_SPLIT_RATIO);
            entity.setCreateTime(now); entity.setUpdateTime(now); entity.setDeleteFlag(0); result.add(entity);
        }
        return result;
    }
    private Map<Long, BigDecimal> equalShares(BigDecimal amount, List<ExpenseShareRequest> requests) {
        long cents = amount.movePointRight(2).longValueExact(); long base = cents / requests.size(), remainder = cents % requests.size();
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int index = 0; index < requests.size(); index++) result.put(requests.get(index).userId(), BigDecimal.valueOf(base + (index < remainder ? 1 : 0), 2));
        return result;
    }
    private Map<Long, BigDecimal> proportionalShares(BigDecimal amount, List<ExpenseShareRequest> requests) {
        BigDecimal totalRatio = requests.stream().map(ExpenseShareRequest::splitRatio).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCents = amount.movePointRight(2).longValueExact();
        List<RatioAllocation> allocations = new ArrayList<>(); long allocatedCents = 0;
        for (ExpenseShareRequest item : requests) {
            BigDecimal rawCents = BigDecimal.valueOf(totalCents).multiply(item.splitRatio()).divide(totalRatio, 12, RoundingMode.DOWN);
            long cents = rawCents.setScale(0, RoundingMode.DOWN).longValueExact();
            allocatedCents += cents;
            allocations.add(new RatioAllocation(item.userId(), cents, rawCents.subtract(BigDecimal.valueOf(cents))));
        }
        allocations.sort(Comparator.comparing(RatioAllocation::fraction).reversed().thenComparing(RatioAllocation::userId));
        long remainder = totalCents - allocatedCents;
        for (int index = 0; index < remainder; index++) allocations.set(index, allocations.get(index).withCents(allocations.get(index).cents() + 1));
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        allocations.stream().sorted(Comparator.comparing(RatioAllocation::userId)).forEach(item -> result.put(item.userId(), BigDecimal.valueOf(item.cents(), 2)));
        return result;
    }
    private BigDecimal storedRatio(ActivityExpenseShareEntity share) { return share.getSplitRatio() == null ? DEFAULT_SPLIT_RATIO : share.getSplitRatio(); }
    private record ShareValue(BigDecimal amount, BigDecimal ratio) {}
    private record RatioAllocation(Long userId, long cents, BigDecimal fraction) {
        RatioAllocation withCents(long value) { return new RatioAllocation(userId, value, fraction); }
    }
    private ActivityExpenseEntity find(Long activityId, Long expenseId) { ActivityExpenseEntity expense = expenseMapper.selectById(expenseId); if (expense == null || !activityId.equals(expense.getActivityId())) throw new NotFoundException("账单不存在"); return expense; }
    private ActivityExpenseEntity findForUpdate(Long activityId, Long expenseId) { ActivityExpenseEntity expense = expenseMapper.selectByIdForUpdate(activityId, expenseId); if (expense == null) throw new NotFoundException("账单不存在"); return expense; }
    private void requireEditable(ActivityExpenseEntity expense, ActivityEntity activity, ActivityMemberEntity operator, Long userId) { if (!"ACTIVE".equals(expense.getStatus())) throw param("已作废账单不能编辑"); if (!userId.equals(expense.getCreatedBy()) && !access.isActivityCreator(activity, operator, userId)) throw new ForbiddenException("仅记录人或活动创建者可以操作账单"); }
    private void requireExpenseOperator(ActivityExpenseEntity expense, ActivityEntity activity, ActivityMemberEntity operator, Long userId) { if (!userId.equals(expense.getCreatedBy()) && !access.isActivityCreator(activity, operator, userId)) throw new ForbiddenException("仅记录人或活动创建者可以操作账单"); }
    private void requireNotCanceled(ActivityEntity activity) { if ("CANCELED".equals(activity.getStatus())) throw new ForbiddenException("活动已取消，仅可查看历史费用"); }
    private ExpenseDetailResponse toDetail(ActivityExpenseEntity expense) {
        return toDetail(expense, shareMapper.selectList(new LambdaQueryWrapper<ActivityExpenseShareEntity>()
                .eq(ActivityExpenseShareEntity::getExpenseId, expense.getId())));
    }
    private ExpenseDetailResponse toDetailAfterLock(ActivityExpenseEntity expense) {
        List<ActivityExpenseShareEntity> shares = shareMapper.selectByExpenseIdForUpdate(expense.getId());
        return toDetail(expense, shares);
    }
    private ExpenseDetailResponse toDetail(ActivityExpenseEntity expense, List<ActivityExpenseShareEntity> shares) {
        Set<Long> ids = new HashSet<>(); ids.add(expense.getPayerUserId()); ids.add(expense.getCreatedBy()); shares.forEach(item -> ids.add(item.getUserId()));
        Map<Long, ParticipantProfile> profiles = memberDisplayService.loadParticipantProfiles(expense.getActivityId(), ids);
        FileEntity receipt = expense.getReceiptFileId() == null ? null : fileMapper.selectById(expense.getReceiptFileId());
        return new ExpenseDetailResponse(expense.getId(), expense.getActivityId(), expense.getTitle(), expense.getCategory(), expense.getAmount(),
                expense.getPayerUserId(), displayName(profiles.get(expense.getPayerUserId())), expense.getCreatedBy(),
                displayName(profiles.get(expense.getCreatedBy())), expense.getExpenseTime(), expense.getSplitMode(), expense.getReceiptFileId(),
                receipt == null ? null : receipt.getUrl(), expense.getDescription(), expense.getStatus(), expense.getVoidReason(), expense.getVersion(),
                shares.stream().map(item -> new ExpenseShareResponse(item.getUserId(), displayName(profiles.get(item.getUserId())),
                        avatarUrl(profiles.get(item.getUserId())), item.getShareAmount(), storedRatio(item))).toList(),
                expense.getCreateTime(), expense.getUpdateTime());
    }
    private List<ExpenseListItemResponse> toListItems(List<ActivityExpenseEntity> expenses, Long currentUserId) {
        if (expenses.isEmpty()) return List.of();
        Set<Long> ids = expenses.stream().map(ActivityExpenseEntity::getId).collect(Collectors.toSet());
        Map<Long,List<ActivityExpenseShareEntity>> shares = shareMapper.selectList(new LambdaQueryWrapper<ActivityExpenseShareEntity>()
                .in(ActivityExpenseShareEntity::getExpenseId, ids)).stream().collect(Collectors.groupingBy(ActivityExpenseShareEntity::getExpenseId));
        Set<Long> payerIds = expenses.stream().map(ActivityExpenseEntity::getPayerUserId).collect(Collectors.toSet());
        Map<Long, ParticipantProfile> profiles = memberDisplayService.loadParticipantProfiles(expenses.getFirst().getActivityId(), payerIds);
        return expenses.stream().map(item -> {
            List<ActivityExpenseShareEntity> lines = shares.getOrDefault(item.getId(), List.of());
            BigDecimal currentShare = lines.stream().filter(line -> currentUserId.equals(line.getUserId()))
                    .map(ActivityExpenseShareEntity::getShareAmount).findFirst().orElse(null);
            return new ExpenseListItemResponse(item.getId(), item.getTitle(), item.getCategory(), item.getAmount(),
                    displayName(profiles.get(item.getPayerUserId())), item.getPayerUserId(), item.getExpenseTime(), lines.size(),
                    currentShare, item.getStatus(), item.getVersion());
        }).toList();
    }
    private String displayName(ParticipantProfile profile) { return profile == null ? "玩伴用户" : profile.displayName(); }
    private String avatarUrl(ParticipantProfile profile) { return profile == null ? null : profile.avatarUrl(); }
    private static BusinessException param(String message) { return new BusinessException(ErrorCode.PARAM_ERROR.code(), message); }
    private static BusinessException versionConflict() { return new BusinessException(ErrorCode.BUSINESS_ERROR.code(), "账单已被其他成员修改，请刷新后重试"); }
    private static String requireClientRequestId(String value) { if (value == null || value.trim().isEmpty()) throw param("新增账单时必须提供 clientRequestId"); String result = value.trim(); if (result.length() > 64) throw param("clientRequestId 长度不能超过 64"); return result; }
}
