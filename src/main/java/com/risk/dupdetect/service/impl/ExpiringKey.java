package com.risk.dupdetect.service.impl;

import com.risk.dupdetect.domain.TransactionKey;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class to pair a TransactionKey with its expiration timestamp.
 * Used inside DelayQueue to clean up expired keys automatically.
 */
public class ExpiringKey implements Delayed {
    private final TransactionKey key;
    private final long expiryTimeMs;

    public ExpiringKey(TransactionKey key, long durationMs) {
        this.key = key;
        this.expiryTimeMs = System.currentTimeMillis() + durationMs;
    }

    public TransactionKey getKey() {
        return key;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = expiryTimeMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this.expiryTimeMs < ((ExpiringKey) o).expiryTimeMs) {
            return -1;
        }
        if (this.expiryTimeMs > ((ExpiringKey) o).expiryTimeMs) {
            return 1;
        }
        return 0;
    }
}
