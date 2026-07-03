package com.risk.dupdetect.service.impl;

import com.risk.dupdetect.domain.TransactionKey;
import com.risk.dupdetect.service.SlidingWindowStore;
import com.risk.dupdetect.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;

/**
 * Thread-safe implementation of SlidingWindowStore using ConcurrentHashMap's key set
 * for O(1) membership check, combined with a DelayQueue for precise TTL eviction.
 */
@Service
@ConditionalOnProperty(name = "transaction.store.type", havingValue = "inmemory", matchIfMissing = true)
public class InMemorySlidingWindowStore implements SlidingWindowStore {

    private final Set<TransactionKey> set = ConcurrentHashMap.newKeySet();
    private final DelayQueue<ExpiringKey> delayQueue = new DelayQueue<>();
    private final SystemConfigService configService;

    @Autowired
    public InMemorySlidingWindowStore(SystemConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean addIfAbsent(TransactionKey key) {
        if (set.add(key)) {
            // Key was not present, so we store it and queue its eviction
            long windowMs = configService.getWindowSeconds() * 1000L;
            delayQueue.add(new ExpiringKey(key, windowMs));
            return true;
        }
        return false; // Key already exists (duplicate)
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public void evictExpired() {
        ExpiringKey expiringKey;
        while ((expiringKey = delayQueue.poll()) != null) {
            set.remove(expiringKey.getKey());
        }
    }

    @Override
    public double getOldestEntryAgeSeconds() {
        ExpiringKey head = delayQueue.peek();
        if (head == null) {
            return 0.0;
        }
        long delayMs = head.getDelay(java.util.concurrent.TimeUnit.MILLISECONDS);
        long windowMs = configService.getWindowSeconds() * 1000L;
        long ageMs = windowMs - delayMs;
        return Math.max(0, ageMs) / 1000.0;
    }

    @Override
    public void clear() {
        set.clear();
        delayQueue.clear();
    }

    // Accessible for tests
    protected Set<TransactionKey> getSet() {
        return set;
    }

    protected DelayQueue<ExpiringKey> getDelayQueue() {
        return delayQueue;
    }
}
