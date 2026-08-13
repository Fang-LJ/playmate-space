package com.playmate.space.service.finance;

import java.util.Optional;

public interface FinanceSnapshotCache {
    Optional<SettlementSnapshot> get(Long activityId, long financeVersion);
    void put(SettlementSnapshot snapshot);
}
