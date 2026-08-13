package com.playmate.space.service.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "playmate.finance.cache", name = "enabled", havingValue = "true")
public class RedisFinanceSnapshotCache implements FinanceSnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(RedisFinanceSnapshotCache.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FinanceCacheProperties properties;

    public RedisFinanceSnapshotCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                     FinanceCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<SettlementSnapshot> get(Long activityId, long financeVersion) {
        String key = key(activityId, financeVersion);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) return Optional.empty();
            SettlementSnapshot snapshot = objectMapper.readValue(value, SettlementSnapshot.class);
            if (!valid(snapshot, activityId, financeVersion)) {
                log.warn("Finance snapshot cache corrupted: activityId={}, financeVersion={}", activityId, financeVersion);
                deleteQuietly(key);
                return Optional.empty();
            }
            log.debug("Finance snapshot cache hit: activityId={}, financeVersion={}", activityId, financeVersion);
            return Optional.of(snapshot);
        } catch (JsonProcessingException exception) {
            log.warn("Finance snapshot cache corrupted: activityId={}, financeVersion={}, reason={}",
                    activityId, financeVersion, exception.getOriginalMessage());
            deleteQuietly(key);
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Finance snapshot cache read failed: activityId={}, financeVersion={}, reason={}",
                    activityId, financeVersion, exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(SettlementSnapshot snapshot) {
        String key = key(snapshot.activityId(), snapshot.financeVersion());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), properties.getTtl());
        } catch (JsonProcessingException exception) {
            log.warn("Finance snapshot cache write failed: activityId={}, financeVersion={}, reason={}",
                    snapshot.activityId(), snapshot.financeVersion(), exception.getOriginalMessage());
        } catch (RuntimeException exception) {
            log.warn("Finance snapshot cache write failed: activityId={}, financeVersion={}, reason={}",
                    snapshot.activityId(), snapshot.financeVersion(), exception.getMessage());
        }
    }

    static String key(Long activityId, long financeVersion) {
        return "playmate:finance:snapshot:v1:" + activityId + ":" + financeVersion;
    }

    private boolean valid(SettlementSnapshot snapshot, Long activityId, long financeVersion) {
        return snapshot != null && snapshot.schemaVersion() == SettlementSnapshot.SCHEMA_VERSION
                && activityId.equals(snapshot.activityId()) && financeVersion == snapshot.financeVersion();
    }

    private void deleteQuietly(String key) {
        try { redisTemplate.delete(key); }
        catch (RuntimeException exception) {
            log.warn("Finance snapshot corrupted cache deletion failed: key={}, reason={}", key, exception.getMessage());
        }
    }
}
