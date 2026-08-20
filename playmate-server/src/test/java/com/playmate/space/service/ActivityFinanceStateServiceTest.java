package com.playmate.space.service;

import com.playmate.space.mapper.ActivityFinanceStateMapper;
import com.playmate.space.entity.ActivityFinanceStateEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityFinanceStateServiceTest {
    @AfterEach
    void cleanTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void absentReadVersionIsZero() {
        ActivityFinanceStateMapper mapper = mock(ActivityFinanceStateMapper.class);
        when(mapper.selectFinanceVersion(10L)).thenReturn(null);

        assertEquals(0L, new ActivityFinanceStateService(mapper).currentVersion(10L));
    }

    @Test
    void existingReadVersionIsReturned() {
        ActivityFinanceStateMapper mapper = mock(ActivityFinanceStateMapper.class);
        when(mapper.selectFinanceVersion(10L)).thenReturn(12L);

        assertEquals(12L, new ActivityFinanceStateService(mapper).currentVersion(10L));
    }

    @Test
    void ensureAndLockInitializesMissingStateAtVersionZero() {
        ActivityFinanceStateMapper mapper = mock(ActivityFinanceStateMapper.class);
        ActivityFinanceStateEntity state = new ActivityFinanceStateEntity();
        state.setActivityId(10L);
        state.setFinanceVersion(0L);
        when(mapper.selectForUpdate(10L)).thenReturn(state);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        ActivityFinanceStateEntity result = new ActivityFinanceStateService(mapper).ensureAndLock(10L);

        assertEquals(0L, result.getFinanceVersion());
        verify(mapper).ensure(10L);
        verify(mapper).selectForUpdate(10L);
    }
}
