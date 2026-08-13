package com.playmate.space.service;

import com.playmate.space.dto.expense.ExpenseDashboardResponse;
import com.playmate.space.dto.expense.ExpenseSuggestionResponse;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityExpenseEntity;
import com.playmate.space.entity.ActivityExpenseShareEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.*;
import com.playmate.space.service.finance.SettlementSnapshot;
import com.playmate.space.service.finance.SettlementSnapshotProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {
    private static final Long ACTIVITY_ID = 10L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long USER_C = 3L;

    @Mock private ActivityCollaborationAccess access;
    @Mock private ActivityExpenseMapper expenseMapper;
    @Mock private ActivityExpenseShareMapper shareMapper;
    @Mock private ActivitySettlementMapper settlementMapper;
    @Mock private ActivityMemberMapper memberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ActivityFinanceStateService financeStateService;
    @Mock private SettlementSnapshotProvider snapshotProvider;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        service = new SettlementService(access, expenseMapper, shareMapper, settlementMapper, memberMapper, userMapper,
                financeStateService, snapshotProvider);
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setActivityId(ACTIVITY_ID);
        member.setUserId(USER_A);
        member.setMemberStatus("ACTIVE");
        when(access.requireUserId()).thenReturn(USER_A);
        when(access.requireActivity(ACTIVITY_ID)).thenReturn(activity);
        when(access.requireActiveMember(ACTIVITY_ID, USER_A)).thenReturn(member);
    }

    @Test
    void dashboardUsesPaidMinusShareAndIgnoresHistoricalTransfers() {
        when(snapshotProvider.getSnapshot(ACTIVITY_ID)).thenReturn(snapshot(
                List.of(account(USER_A, "40.00", "232.67"), account(USER_B, "0.00", "152.67"), account(USER_C, "518.00", "172.66")),
                List.of(new SettlementSnapshot.Suggestion(USER_A, USER_C, new BigDecimal("192.67")),
                        new SettlementSnapshot.Suggestion(USER_B, USER_C, new BigDecimal("152.67"))),
                2, 3, "558.00", 12L));
        when(userMapper.selectByIds(any())).thenReturn(List.of(
                user(USER_A, "A"), user(USER_B, "B"), user(USER_C, "C")
        ));
        ExpenseDashboardResponse dashboard = service.dashboard(ACTIVITY_ID);

        assertEquals(new BigDecimal("558.00"), dashboard.summary().totalExpenseAmount());
        assertEquals(2, dashboard.summary().expenseCount());
        assertEquals(3, dashboard.summary().participantCount());
        assertEquals(new BigDecimal("-192.67"), dashboard.members().get(0).netAmount());
        assertEquals(new BigDecimal("-152.67"), dashboard.members().get(1).netAmount());
        assertEquals(new BigDecimal("345.34"), dashboard.members().get(2).netAmount());
        assertEquals(List.of(new BigDecimal("192.67"), new BigDecimal("152.67")),
                dashboard.suggestions().stream().map(ExpenseSuggestionResponse::amount).toList());
        assertEquals(new BigDecimal("345.34"), dashboard.suggestions().stream()
                .map(ExpenseSuggestionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(12L, dashboard.financeVersion());
        verifyNoInteractions(settlementMapper);
    }

    @Test
    void balancedAccountsProduceNoSuggestions() {
        when(snapshotProvider.getSnapshot(ACTIVITY_ID)).thenReturn(snapshot(
                List.of(account(USER_A, "20.00", "20.00")), List.of(), 1, 1, "20.00", 0L));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(USER_A, "A")));

        ExpenseDashboardResponse dashboard = service.dashboard(ACTIVITY_ID);

        assertTrue(dashboard.suggestions().isEmpty());
        assertEquals("无需结算", dashboard.members().getFirst().settlementText());
        verifyNoInteractions(settlementMapper);
    }

    @Test
    void dashboardUsesFreshNicknameWithoutChangingFinanceSnapshot() {
        when(snapshotProvider.getSnapshot(ACTIVITY_ID)).thenReturn(snapshot(
                List.of(account(USER_A, "10.00", "10.00")), List.of(), 1, 1, "10.00", 8L));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(USER_A, "旧昵称")), List.of(user(USER_A, "新昵称")));

        ExpenseDashboardResponse first = service.dashboard(ACTIVITY_ID);
        ExpenseDashboardResponse second = service.dashboard(ACTIVITY_ID);

        assertEquals("旧昵称", first.members().getFirst().nickname());
        assertEquals("新昵称", second.members().getFirst().nickname());
        verify(snapshotProvider, times(2)).getSnapshot(ACTIVITY_ID);
    }

    private ActivityExpenseEntity expense(Long id, Long payerId, String amount) {
        ActivityExpenseEntity entity = new ActivityExpenseEntity();
        entity.setId(id);
        entity.setActivityId(ACTIVITY_ID);
        entity.setTitle("账单 " + id);
        entity.setCategory("FOOD");
        entity.setAmount(new BigDecimal(amount));
        entity.setPayerUserId(payerId);
        entity.setStatus("ACTIVE");
        entity.setVersion(1);
        return entity;
    }

    private ActivityExpenseShareEntity share(Long expenseId, Long userId, String amount) {
        ActivityExpenseShareEntity entity = new ActivityExpenseShareEntity();
        entity.setExpenseId(expenseId);
        entity.setUserId(userId);
        entity.setShareAmount(new BigDecimal(amount));
        return entity;
    }

    private UserEntity user(Long id, String nickname) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setNickname(nickname);
        return entity;
    }

    private SettlementSnapshot snapshot(List<SettlementSnapshot.Account> accounts,
                                        List<SettlementSnapshot.Suggestion> suggestions,
                                        int expenseCount, int participantCount, String total, long financeVersion) {
        return new SettlementSnapshot(SettlementSnapshot.SCHEMA_VERSION, ACTIVITY_ID, financeVersion,
                new BigDecimal(total), expenseCount, participantCount, accounts, suggestions, List.of());
    }

    private SettlementSnapshot.Account account(Long userId, String paid, String share) {
        BigDecimal paidAmount = new BigDecimal(paid);
        BigDecimal shareAmount = new BigDecimal(share);
        return new SettlementSnapshot.Account(userId, paidAmount, shareAmount, paidAmount.subtract(shareAmount));
    }
}
