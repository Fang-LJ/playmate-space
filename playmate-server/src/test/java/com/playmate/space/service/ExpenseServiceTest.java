package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.dto.expense.ExpenseShareRequest;
import com.playmate.space.dto.expense.SaveExpenseRequest;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityExpenseEntity;
import com.playmate.space.entity.ActivityExpenseShareEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {
    private static final Long ACTIVITY_ID = 10L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Mock private ActivityCollaborationAccess access;
    @Mock private ActivityExpenseMapper expenseMapper;
    @Mock private ActivityExpenseShareMapper shareMapper;
    @Mock private ActivityMemberMapper memberMapper;
    @Mock private UserMapper userMapper;
    @Mock private FileMapper fileMapper;
    @Mock private SettlementService settlementService;

    private ExpenseService service;
    private ActivityEntity activity;
    private ActivityMemberEntity operator;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(access, expenseMapper, shareMapper, memberMapper, userMapper, fileMapper, settlementService);
        activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setCreatorUserId(USER_A);
        activity.setStatus("PLANNING");
        operator = new ActivityMemberEntity();
        operator.setActivityId(ACTIVITY_ID);
        operator.setUserId(USER_A);
        operator.setRole("CREATOR");
        operator.setMemberStatus("ACTIVE");
        when(access.requireUserId()).thenReturn(USER_A);
        when(access.requireActivity(ACTIVITY_ID)).thenReturn(activity);
        when(access.requireActiveMember(ACTIVITY_ID, USER_A)).thenReturn(operator);
        lenient().when(access.requireActiveMember(ACTIVITY_ID, USER_B)).thenReturn(activeMember(USER_B));
    }

    @Test
    void equalSplitAssignsRemainderByStableUserOrder() {
        when(access.isActivityCreator(activity, operator, USER_A)).thenReturn(true);
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(USER_A), user(USER_B)));
        when(shareMapper.selectList(any())).thenAnswer(invocation -> insertedShares());

        service.create(ACTIVITY_ID, request(USER_A, "100.01", "EQUAL", null,
                List.of(new ExpenseShareRequest(USER_B, null), new ExpenseShareRequest(USER_A, null))));

        ArgumentCaptor<ActivityExpenseShareEntity> captor = ArgumentCaptor.forClass(ActivityExpenseShareEntity.class);
        verify(shareMapper, times(2)).insert(captor.capture());
        assertEquals(USER_A, captor.getAllValues().get(0).getUserId());
        assertEquals(new BigDecimal("50.01"), captor.getAllValues().get(0).getShareAmount());
        assertEquals(USER_B, captor.getAllValues().get(1).getUserId());
        assertEquals(new BigDecimal("50.00"), captor.getAllValues().get(1).getShareAmount());
    }

    @Test
    void customSplitRejectsMismatchedTotal() {
        when(access.isActivityCreator(activity, operator, USER_A)).thenReturn(true);
        SaveExpenseRequest request = request(USER_A, "100.00", "CUSTOM", null,
                List.of(new ExpenseShareRequest(USER_A, new BigDecimal("20.00")),
                        new ExpenseShareRequest(USER_B, new BigDecimal("70.00"))));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(ACTIVITY_ID, request));

        assertTrue(error.getMessage().contains("必须等于总金额"));
        verify(expenseMapper, never()).insert(any(ActivityExpenseEntity.class));
    }

    @Test
    void ordinaryMemberCannotRecordAnotherPayerButCreatorCan() {
        when(access.isActivityCreator(activity, operator, USER_A)).thenReturn(false, true);
        SaveExpenseRequest request = request(USER_B, "10.00", "EQUAL", null,
                List.of(new ExpenseShareRequest(USER_A, null)));

        assertThrows(ForbiddenException.class, () -> service.create(ACTIVITY_ID, request));

        when(userMapper.selectByIds(any())).thenReturn(List.of(user(USER_A), user(USER_B)));
        when(shareMapper.selectList(any())).thenReturn(List.of());
        service.create(ACTIVITY_ID, request);
        verify(expenseMapper).insert(any(ActivityExpenseEntity.class));
    }

    @Test
    void atomicVersionUpdateSucceedsAndIncrementsVersion() {
        ActivityExpenseEntity existing = existingExpense(88L, 3);
        when(expenseMapper.selectById(88L)).thenReturn(existing);
        when(access.isActivityCreator(activity, operator, USER_A)).thenReturn(true);
        when(expenseMapper.updateActiveByVersion(any(), eq(3), any())).thenReturn(1);
        when(shareMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(USER_A)));

        var result = service.update(ACTIVITY_ID, 88L, request(USER_A, "12.00", "EQUAL", 3,
                List.of(new ExpenseShareRequest(USER_A, null))));

        assertEquals(4, result.version());
        verify(expenseMapper).updateActiveByVersion(existing, 3, existing.getUpdateTime());
        verify(shareMapper).deleteByExpenseId(88L);
    }

    @Test
    void staleVersionFailsAtomicallyBeforeReplacingShares() {
        ActivityExpenseEntity existing = existingExpense(89L, 4);
        when(expenseMapper.selectById(89L)).thenReturn(existing);
        when(access.isActivityCreator(activity, operator, USER_A)).thenReturn(true);
        when(expenseMapper.updateActiveByVersion(any(), eq(3), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(
                ACTIVITY_ID, 89L, request(USER_A, "12.00", "EQUAL", 3,
                        List.of(new ExpenseShareRequest(USER_A, null)))));

        assertEquals("账单已被其他成员修改，请刷新后重试", error.getMessage());
        verify(shareMapper, never()).deleteByExpenseId(anyLong());
    }

    private List<ActivityExpenseShareEntity> insertedShares() {
        return List.of();
    }

    private SaveExpenseRequest request(Long payerId, String amount, String splitMode, Integer version,
                                       List<ExpenseShareRequest> shares) {
        return new SaveExpenseRequest("测试账单", "FOOD", new BigDecimal(amount), payerId,
                LocalDateTime.of(2026, 8, 11, 12, 0), splitMode, shares,
                null, null, version);
    }

    private ActivityExpenseEntity existingExpense(Long id, int version) {
        ActivityExpenseEntity entity = new ActivityExpenseEntity();
        entity.setId(id);
        entity.setActivityId(ACTIVITY_ID);
        entity.setTitle("旧账单");
        entity.setCategory("FOOD");
        entity.setAmount(new BigDecimal("10.00"));
        entity.setPayerUserId(USER_A);
        entity.setSplitMode("EQUAL");
        entity.setExpenseTime(LocalDateTime.of(2026, 8, 11, 11, 0));
        entity.setCreatedBy(USER_A);
        entity.setStatus("ACTIVE");
        entity.setVersion(version);
        entity.setDeleteFlag(0);
        return entity;
    }

    private ActivityMemberEntity activeMember(Long userId) {
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setActivityId(ACTIVITY_ID);
        member.setUserId(userId);
        member.setRole("MEMBER");
        member.setMemberStatus("ACTIVE");
        return member;
    }

    private UserEntity user(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setNickname("用户 " + userId);
        return user;
    }
}
