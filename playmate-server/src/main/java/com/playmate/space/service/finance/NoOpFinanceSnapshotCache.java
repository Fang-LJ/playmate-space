package com.playmate.space.service.finance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "playmate.finance.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpFinanceSnapshotCache implements FinanceSnapshotCache {
    @Override public Optional<SettlementSnapshot> get(Long activityId, long financeVersion) { return Optional.empty(); }
    @Override public void put(SettlementSnapshot snapshot) { }
}
