package com.risk.dupdetect.scheduler;

import com.risk.dupdetect.service.SlidingWindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to run eviction of expired keys in the background.
 */
@Component
public class WindowEvictionScheduler {

    private final SlidingWindowStore slidingWindowStore;

    @Autowired
    public WindowEvictionScheduler(SlidingWindowStore slidingWindowStore) {
        this.slidingWindowStore = slidingWindowStore;
    }

    /**
     * Periodically triggers cleanup of expired transaction keys.
     * Runs every second with a fixed delay.
     */
    @Scheduled(fixedDelayString = "${transaction.eviction.fixed-delay-ms:1000}")
    public void evictExpiredKeys() {
        slidingWindowStore.evictExpired();
    }
}
