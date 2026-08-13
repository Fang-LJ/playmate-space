package com.playmate.space.service.finance;

import com.playmate.space.entity.ActivityExpenseEntity;
import com.playmate.space.entity.ActivityExpenseShareEntity;
import com.playmate.space.mapper.ActivityExpenseMapper;
import com.playmate.space.mapper.ActivityExpenseShareMapper;
import com.playmate.space.service.ActivityFinanceStateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceSnapshotLoaderTest {
    @Test
    void buildsPureFinancialFactsWithoutUserProfiles() {
        ActivityFinanceStateService financeStateService = mock(ActivityFinanceStateService.class);
        ActivityExpenseMapper expenseMapper = mock(ActivityExpenseMapper.class);
        ActivityExpenseShareMapper shareMapper = mock(ActivityExpenseShareMapper.class);
        when(financeStateService.currentVersion(10L)).thenReturn(8L);
        when(expenseMapper.selectList(any())).thenReturn(List.of(expense(101L, 1L, "100.00")));
        when(shareMapper.selectList(any())).thenReturn(List.of(share(101L, 1L, "40.00"), share(101L, 2L, "60.00")));

        SettlementSnapshot snapshot = new FinanceSnapshotLoader(financeStateService, expenseMapper, shareMapper).load(10L);

        assertEquals(SettlementSnapshot.SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals(8L, snapshot.financeVersion());
        assertEquals(new BigDecimal("100.00"), snapshot.totalExpenseAmount());
        assertEquals(2, snapshot.participantCount());
        assertEquals(new BigDecimal("60.00"), snapshot.accounts().getFirst().netAmount());
        assertEquals(new BigDecimal("-60.00"), snapshot.accounts().getLast().netAmount());
        assertEquals(new SettlementSnapshot.Suggestion(2L, 1L, new BigDecimal("60.00")), snapshot.suggestions().getFirst());
        assertEquals(1L, snapshot.recentExpenses().getFirst().payerUserId());
    }

    private ActivityExpenseEntity expense(Long id, Long payerUserId, String amount) {
        ActivityExpenseEntity expense = new ActivityExpenseEntity();
        expense.setId(id);
        expense.setPayerUserId(payerUserId);
        expense.setAmount(new BigDecimal(amount));
        expense.setStatus("ACTIVE");
        expense.setVersion(1);
        expense.setTitle("测试账单");
        expense.setCategory("FOOD");
        return expense;
    }

    private ActivityExpenseShareEntity share(Long expenseId, Long userId, String amount) {
        ActivityExpenseShareEntity share = new ActivityExpenseShareEntity();
        share.setExpenseId(expenseId);
        share.setUserId(userId);
        share.setShareAmount(new BigDecimal(amount));
        return share;
    }
}
