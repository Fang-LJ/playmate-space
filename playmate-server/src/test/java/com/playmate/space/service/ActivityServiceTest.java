package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.FileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    private static final Long ACTIVITY_ID = 10L;
    private static final Long CREATOR_ID = 1L;

    @Mock private ActivityMapper activityMapper;
    @Mock private ActivityMemberMapper activityMemberMapper;
    @Mock private FileMapper fileMapper;
    @Mock private ActivityTodoLifecycleService todoLifecycleService;
    @Mock private ActivityFinanceStateService financeStateService;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setUserId(CREATOR_ID);
        service = new ActivityService(activityMapper, activityMemberMapper, fileMapper, todoLifecycleService, financeStateService);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void cancelLocksFinanceStateThenRereadsLatestActivityWithoutChangingFinanceVersion() {
        ActivityEntity beforeLock = activity("PLANNING");
        ActivityEntity afterLock = activity("PLANNING");
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(beforeLock, afterLock);
        when(activityMapper.selectByIdForUpdate(ACTIVITY_ID)).thenReturn(afterLock);
        when(activityMemberMapper.selectOne(any())).thenReturn(creatorMember());

        var result = service.cancelActivity(ACTIVITY_ID);

        assertEquals("CANCELED", result.getStatus());
        InOrder order = inOrder(activityMapper, activityMemberMapper, financeStateService, todoLifecycleService);
        order.verify(activityMapper).selectById(ACTIVITY_ID);
        order.verify(activityMemberMapper).selectOne(any());
        order.verify(financeStateService).ensureAndLock(ACTIVITY_ID);
        order.verify(activityMapper).selectByIdForUpdate(ACTIVITY_ID);
        order.verify(activityMemberMapper).selectOne(any());
        order.verify(activityMapper).updateById(afterLock);
        order.verify(todoLifecycleService).cancelActivityPendingTodos(ACTIVITY_ID);
        verify(financeStateService, never()).incrementVersion(anyLong());
    }

    @Test
    void cancelRejectsWhenLatestActivityReadAfterLockIsAlreadyCanceled() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity("PLANNING"), activity("CANCELED"));
        when(activityMapper.selectByIdForUpdate(ACTIVITY_ID)).thenReturn(activity("CANCELED"));
        when(activityMemberMapper.selectOne(any())).thenReturn(creatorMember());

        BusinessException error = assertThrows(BusinessException.class, () -> service.cancelActivity(ACTIVITY_ID));

        assertEquals("活动已取消，不能重复取消", error.getMessage());
        InOrder order = inOrder(activityMapper, activityMemberMapper, financeStateService);
        order.verify(activityMapper).selectById(ACTIVITY_ID);
        order.verify(activityMemberMapper).selectOne(any());
        order.verify(financeStateService).ensureAndLock(ACTIVITY_ID);
        order.verify(activityMapper).selectByIdForUpdate(ACTIVITY_ID);
        order.verify(activityMemberMapper).selectOne(any());
        verify(activityMapper, never()).updateById(any(ActivityEntity.class));
        verify(todoLifecycleService, never()).cancelActivityPendingTodos(anyLong());
        verify(financeStateService, never()).incrementVersion(anyLong());
    }

    @Test
    void cancelUsesFinanceLockForActivityWithoutExistingExpenseState() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity("PLANNING"), activity("PLANNING"));
        when(activityMapper.selectByIdForUpdate(ACTIVITY_ID)).thenReturn(activity("PLANNING"));
        when(activityMemberMapper.selectOne(any())).thenReturn(creatorMember());

        service.cancelActivity(ACTIVITY_ID);

        verify(financeStateService).ensureAndLock(ACTIVITY_ID);
        verify(financeStateService, never()).incrementVersion(anyLong());
    }

    private ActivityEntity activity(String status) {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setCreatorUserId(CREATOR_ID);
        activity.setStatus(status);
        return activity;
    }

    private ActivityMemberEntity creatorMember() {
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setActivityId(ACTIVITY_ID);
        member.setUserId(CREATOR_ID);
        member.setRole("CREATOR");
        member.setMemberStatus("ACTIVE");
        return member;
    }
}
