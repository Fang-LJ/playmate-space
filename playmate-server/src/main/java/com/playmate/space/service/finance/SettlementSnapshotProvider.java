package com.playmate.space.service.finance;

import com.playmate.space.service.ActivityFinanceStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettlementSnapshotProvider {
    private static final Logger log = LoggerFactory.getLogger(SettlementSnapshotProvider.class);
    private final ActivityFinanceStateService financeStateService;
    private final FinanceSnapshotLoader loader;
    private final FinanceSnapshotCache cache;

    public SettlementSnapshotProvider(ActivityFinanceStateService financeStateService, FinanceSnapshotLoader loader,
                                      FinanceSnapshotCache cache) {
        this.financeStateService = financeStateService;
        this.loader = loader;
        this.cache = cache;
    }

    public SettlementSnapshot getSnapshot(Long activityId) {
        long requestedVersion = financeStateService.currentVersion(activityId);
        var cached = cache.get(activityId, requestedVersion);
        if (cached.isPresent()) return cached.get();
        log.debug("Finance snapshot cache miss: activityId={}, financeVersion={}", activityId, requestedVersion);
        SettlementSnapshot snapshot = loader.load(activityId);
        // The loader owns the consistent read view; it may legitimately observe a newer committed finance version.
        cache.put(snapshot);
        return snapshot;
    }
}
