package com.risk.dupdetect.service.impl;

import com.risk.dupdetect.domain.TransactionKey;
import com.risk.dupdetect.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class InMemorySlidingWindowStoreTest {

    private SystemConfigService configService;
    private InMemorySlidingWindowStore store;

    @BeforeEach
    public void setUp() {
        configService = new SystemConfigService();
        store = new InMemorySlidingWindowStore(configService);
    }

    @Test
    public void testAddIfAbsentSuccessAndDuplicate() {
        TransactionKey key1 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey key2 = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        TransactionKey key3 = new TransactionKey("payer1", "payee1", new BigDecimal("20.00"), "USD", "ref1");

        assertTrue(store.addIfAbsent(key1));
        assertFalse(store.addIfAbsent(key2)); // duplicate, should return false
        assertTrue(store.addIfAbsent(key3));  // distinct transaction

        assertEquals(2, store.size());
    }

    @Test
    public void testEvictionCorrectness() {
        // Set sliding window to 1 second
        configService.setWindowSeconds(1);

        TransactionKey key = new TransactionKey("payer1", "payee1", new BigDecimal("10.00"), "USD", "ref1");
        assertTrue(store.addIfAbsent(key));
        assertEquals(1, store.size());

        // Immediately try evicting, key should stay since it hasn't expired yet
        store.evictExpired();
        assertEquals(1, store.size());

        // Awaitility polls until the eviction removes the key after 1 second
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            store.evictExpired();
            assertEquals(0, store.size());
        });
    }

    @Test
    public void testConcurrentAdds() throws InterruptedException {
        int threads = 8;
        int ops = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        configService.setWindowSeconds(60);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < ops; i++) {
                    TransactionKey key = new TransactionKey(
                            "payer" + threadId,
                            "payee" + i,
                            new BigDecimal("10.00"),
                            "USD",
                            "ref-" + threadId + "-" + i
                    );
                    assertTrue(store.addIfAbsent(key));
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(threads * ops, store.size());
    }
}
