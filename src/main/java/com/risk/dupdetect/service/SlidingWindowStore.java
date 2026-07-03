package com.risk.dupdetect.service;

import com.risk.dupdetect.domain.TransactionKey;

/**
 * Sliding Window Store interface to hold recent transaction keys.
 */
public interface SlidingWindowStore {
    /**
     * Attempts to add a transaction key to the sliding window.
     * 
     * @param key the transaction key
     * @return true if the key was successfully added (new transaction),
     *         false if the key already exists in the window (duplicate detected)
     */
    boolean addIfAbsent(TransactionKey key);

    /**
     * Returns the current number of active keys in the sliding window.
     * 
     * @return count of keys
     */
    int size();

    /**
     * Evicts expired keys from the window.
     */
    void evictExpired();

    /**
     * Returns the age of the oldest entry in the window in seconds.
     * 
     * @return age in seconds, or 0.0 if empty
     */
    double getOldestEntryAgeSeconds();

    /**
     * Completely clears the store.
     */
    void clear();
}
