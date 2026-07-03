package com.risk.dupdetect.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.risk.dupdetect.domain.TransactionKey;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Probabilistic pre-check using Google Guava's Bloom Filter.
 * A "false" result guarantees no duplicate; a "true" requires exact window lookup.
 */
@Service
public class BloomFilterService {

    private final AtomicReference<BloomFilter<String>> bloomFilterRef =
            new AtomicReference<>(newFilter());

    private static BloomFilter<String> newFilter() {
        return BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 10_000_000, 0.01);
    }

    public boolean mightContain(TransactionKey key) {
        return bloomFilterRef.get().mightContain(toBloomKey(key));
    }

    public void put(TransactionKey key) {
        bloomFilterRef.get().put(toBloomKey(key));
    }

    /** Resets the Bloom filter — use ONLY for testing / admin purposes. */
    public void reset() {
        bloomFilterRef.set(newFilter());
    }

    private String toBloomKey(TransactionKey key) {
        return key.getPayerId() + "|" +
               key.getPayeeId() + "|" +
               key.getAmount().setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "|" +
               key.getCurrency() + "|" +
               key.getIdempotencyRef();
    }
}

