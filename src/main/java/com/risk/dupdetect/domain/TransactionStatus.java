package com.risk.dupdetect.domain;

/**
 * Status of a transaction in the system.
 */
public enum TransactionStatus {
    /**
     * Transaction was successfully posted (no duplicates found).
     */
    POSTED,

    /**
     * Transaction was an exact duplicate (Tier-1 match) and auto-suppressed.
     */
    SUPPRESSED,

    /**
     * Transaction was a probable duplicate (Tier-2 match) and flagged for analyst review.
     */
    FLAGGED
}
