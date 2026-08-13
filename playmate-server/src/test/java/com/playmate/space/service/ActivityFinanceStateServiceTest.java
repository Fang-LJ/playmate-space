package com.playmate.space.service;

import com.playmate.space.mapper.ActivityFinanceStateMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityFinanceStateServiceTest {
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
}
