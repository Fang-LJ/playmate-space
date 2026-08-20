package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityMemberServiceTest {
    private static final Long ACTIVITY_ID = 10L;
    private static final Long CREATOR_ID = 1L;
    private static final Long TARGET_USER_ID = 2L;
    private static final Long TARGET_MEMBER_ID = 22L;

    @Mock private ActivityMapper activityMapper;
    @Mock private ActivityMemberMapper memberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ActivityTodoLifecycleService todoLifecycleService;
    @Mock private SettlementService settlementService;
    @Mock private ActivityFinanceStateService financeStateService;

    private ActivityMemberService service;
    private ActivityMemberEntity target;

    @BeforeEach
    void setUp() {
        service = new ActivityMemberService(activityMapper, memberMapper, userMapper, todoLifecycleService,
                settlementService, financeStateService);
        LoginUserContext.setUserId(CREATOR_ID);

        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID); activity.setCreatorUserId(CREATOR_ID);
        ActivityMemberEntity operator = member(11L, CREATOR_ID, "CREATOR", "ACTIVE");
        target = member(TARGET_MEMBER_ID, TARGET_USER_ID, "MEMBER", "ACTIVE");

        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(memberMapper.selectOne(any())).thenReturn(operator);
        when(memberMapper.selectById(TARGET_MEMBER_ID)).thenReturn(target, target);
        lenient().when(activityMapper.decrementMemberCount(org.mockito.ArgumentMatchers.eq(ACTIVITY_ID), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() { LoginUserContext.clear(); }

    @Test
    void memberWithRemainingBalanceCannotBeRemoved() {
        when(settlementService.remainingNet(ACTIVITY_ID, TARGET_USER_ID)).thenReturn(new BigDecimal("1.00"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.removeMember(ACTIVITY_ID, TARGET_MEMBER_ID));

        assertEquals("该成员仍有未结清费用，不能移除", error.getMessage());
        verify(financeStateService).ensureAndLock(ACTIVITY_ID);
        verify(memberMapper, never()).updateById(org.mockito.ArgumentMatchers.<ActivityMemberEntity>any());
    }

    @Test
    void balancedMemberCanBeRemoved() {
        when(settlementService.remainingNet(ACTIVITY_ID, TARGET_USER_ID)).thenReturn(new BigDecimal("0.00"));

        service.removeMember(ACTIVITY_ID, TARGET_MEMBER_ID);

        assertEquals("REMOVED", target.getMemberStatus());
        verify(memberMapper).updateById(target);
        verify(activityMapper).decrementMemberCount(org.mockito.ArgumentMatchers.eq(ACTIVITY_ID), any());
        verify(todoLifecycleService).cancelUserPendingTodos(ACTIVITY_ID, TARGET_USER_ID);
    }

    @Test
    void memberRemovalLocksFinanceStateBeforeRereadAndBalanceCheck() {
        when(settlementService.remainingNet(ACTIVITY_ID, TARGET_USER_ID)).thenReturn(BigDecimal.ZERO);

        service.removeMember(ACTIVITY_ID, TARGET_MEMBER_ID);

        InOrder order = inOrder(memberMapper, financeStateService, settlementService);
        order.verify(memberMapper).selectById(TARGET_MEMBER_ID);
        order.verify(financeStateService).ensureAndLock(ACTIVITY_ID);
        order.verify(memberMapper).selectById(TARGET_MEMBER_ID);
        order.verify(settlementService).remainingNet(ACTIVITY_ID, TARGET_USER_ID);
        order.verify(memberMapper).updateById(target);
    }

    private ActivityMemberEntity member(Long id, Long userId, String role, String status) {
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setId(id); member.setActivityId(ACTIVITY_ID); member.setUserId(userId);
        member.setRole(role); member.setMemberStatus(status);
        return member;
    }
}
