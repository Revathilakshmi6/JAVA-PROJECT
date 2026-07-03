package com.risk.dupdetect.service.impl;

import com.risk.dupdetect.domain.TransactionKey;
import com.risk.dupdetect.service.SlidingWindowStore;
import com.risk.dupdetect.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Duration;

/**
 * Distributed sliding window store backed by Redis.
 * Uses SET key val NX EX to perform atomic membership test + insertion.
 * NX (only set if Not eXists) = duplicate if SET returns false.
 * TTL handles automatic eviction — no background scheduler required.
 * This is the correct implementation for multi-instance deployments.
 */
@Service
@ConditionalOnProperty(name = "transaction.store.type", havingValue = "redis")
@Slf4j
public class RedisSlidingWindowStore implements SlidingWindowStore {

    private static final String KEY_PREFIX = "dupdetect:window:";

    private final StringRedisTemplate redisTemplate;
    private final SystemConfigService configService;

    @Autowired
    public RedisSlidingWindowStore(StringRedisTemplate redisTemplate, SystemConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    @Override
    public boolean addIfAbsent(TransactionKey key) {
        String redisKey = KEY_PREFIX + toKeyString(key);
        Duration ttl = Duration.ofSeconds(configService.getWindowSeconds());
        // SET key "1" NX EX <seconds> — atomic add-if-not-exists
        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", ttl);
        boolean isNew = Boolean.TRUE.equals(wasAbsent);
        if (!isNew) {
            log.warn("Redis: Duplicate key detected: {}", redisKey);
        }
        return isNew;
    }

    @Override
    public int size() {
        // Redis doesn't provide a cheap count of TTL-scoped keys.
        // Return 0 or implement a counter key in production.
        return 0;
    }

    @Override
    public void evictExpired() {
        // Redis TTL handles eviction automatically — no-op here.
    }

    @Override
    public double getOldestEntryAgeSeconds() {
        // Without a dedicated sorted-set tracker, this is unavailable for Redis.
        return 0.0;
    }

    @Override
    public void clear() {
        // Flush all window keys by scanning the prefix — safe for single-use test scenarios.
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String toKeyString(TransactionKey key) {
        return key.getPayerId() + ":" +
               key.getPayeeId() + ":" +
               key.getAmount().setScale(2, RoundingMode.HALF_EVEN).toPlainString() + ":" +
               key.getCurrency() + ":" +
               key.getIdempotencyRef();
    }
}
