package com.playmate.space.service.finance;

import com.playmate.space.service.ActivityFinanceStateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class SettlementSnapshotProviderTest {
    @Test
    void cacheHitSkipsSnapshotLoader() {
        ActivityFinanceStateService state = mock(ActivityFinanceStateService.class);
        FinanceSnapshotLoader loader = mock(FinanceSnapshotLoader.class);
        FinanceSnapshotCache cache = mock(FinanceSnapshotCache.class);
        SettlementSnapshot snapshot = snapshot(5L);
        when(state.currentVersion(100L)).thenReturn(5L);
        when(cache.get(100L, 5L)).thenReturn(Optional.of(snapshot));

        SettlementSnapshot actual = new SettlementSnapshotProvider(state, loader, cache).getSnapshot(100L);

        assertSame(snapshot, actual);
        verifyNoInteractions(loader);
        verify(cache, never()).put(any());
    }

    @Test
    void cacheMissUsesLoaderActualVersionForWrite() {
        ActivityFinanceStateService state = mock(ActivityFinanceStateService.class);
        FinanceSnapshotLoader loader = mock(FinanceSnapshotLoader.class);
        FinanceSnapshotCache cache = mock(FinanceSnapshotCache.class);
        SettlementSnapshot loaded = snapshot(6L);
        when(state.currentVersion(100L)).thenReturn(5L);
        when(cache.get(100L, 5L)).thenReturn(Optional.empty());
        when(loader.load(100L)).thenReturn(loaded);

        SettlementSnapshot actual = new SettlementSnapshotProvider(state, loader, cache).getSnapshot(100L);

        assertSame(loaded, actual);
        verify(cache).get(100L, 5L);
        verify(cache).put(loaded);
    }

    private SettlementSnapshot snapshot(long financeVersion) {
        return new SettlementSnapshot(SettlementSnapshot.SCHEMA_VERSION, 100L, financeVersion,
                new BigDecimal("0.00"), 0, 0, List.of(), List.of(), List.of());
    }
}
