package com.playmate.space.service.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpFinanceSnapshotCacheTest {
    @Test
    void disabledCacheAlwaysMissesWithoutAnyExternalDependency() {
        NoOpFinanceSnapshotCache cache = new NoOpFinanceSnapshotCache();
        cache.put(new SettlementSnapshot(SettlementSnapshot.SCHEMA_VERSION, 1L, 0L,
                new BigDecimal("0.00"), 0, 0, List.of(), List.of(), List.of()));

        assertTrue(cache.get(1L, 0L).isEmpty());
    }
}
