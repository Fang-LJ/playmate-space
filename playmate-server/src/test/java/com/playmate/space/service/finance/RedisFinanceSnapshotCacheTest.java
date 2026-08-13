package com.playmate.space.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisFinanceSnapshotCacheTest {
    @SuppressWarnings("unchecked")
    @Test
    void corruptedJsonIsIgnoredAndDeleted() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(RedisFinanceSnapshotCache.key(1L, 2L))).thenReturn("not-json");

        RedisFinanceSnapshotCache cache = cache(redis);

        assertTrue(cache.get(1L, 2L).isEmpty());
        verify(redis).delete(RedisFinanceSnapshotCache.key(1L, 2L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void mismatchedSnapshotVersionIsIgnoredAndDeleted() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(values.get(RedisFinanceSnapshotCache.key(1L, 2L)))
                .thenReturn(objectMapper.writeValueAsString(new SettlementSnapshot(SettlementSnapshot.SCHEMA_VERSION, 1L, 1L,
                        new BigDecimal("0.00"), 0, 0, List.of(), List.of(), List.of())));

        assertTrue(cache(redis).get(1L, 2L).isEmpty());

        verify(redis).delete(RedisFinanceSnapshotCache.key(1L, 2L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void redisReadFailureFallsBackToMissAndWriteFailureDoesNotEscape() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        RedisFinanceSnapshotCache cache = cache(redis);

        assertTrue(cache.get(1L, 2L).isEmpty());
        cache.put(snapshot());
    }

    @SuppressWarnings("unchecked")
    @Test
    void readsAndWritesVersionedReadableJson() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SettlementSnapshot snapshot = snapshot();
        when(values.get(RedisFinanceSnapshotCache.key(1L, 2L))).thenReturn(objectMapper.writeValueAsString(snapshot));

        RedisFinanceSnapshotCache cache = new RedisFinanceSnapshotCache(redis, objectMapper, properties());

        assertTrue(cache.get(1L, 2L).isPresent());
        cache.put(snapshot);
        verify(values).set(eq(RedisFinanceSnapshotCache.key(1L, 2L)), contains("financeVersion"), eq(Duration.ofMinutes(15)));
    }

    private RedisFinanceSnapshotCache cache(StringRedisTemplate redis) {
        return new RedisFinanceSnapshotCache(redis, new ObjectMapper().findAndRegisterModules(), properties());
    }

    private FinanceCacheProperties properties() {
        FinanceCacheProperties properties = new FinanceCacheProperties();
        properties.setTtl(Duration.ofMinutes(15));
        return properties;
    }

    private SettlementSnapshot snapshot() {
        return new SettlementSnapshot(SettlementSnapshot.SCHEMA_VERSION, 1L, 2L, new BigDecimal("1.00"),
                1, 1, List.of(new SettlementSnapshot.Account(1L, new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("0.00"))),
                List.of(), List.of());
    }
}
