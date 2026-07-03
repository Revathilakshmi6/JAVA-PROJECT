package com.risk.dupdetect.domain;

/**
 * Matching tier for duplicate transactions.
 */
public enum MatchTier {
    /**
     * Exact match: auto-suppressed.
     */
    EXACT,

    /**
     * Probable match: flagged for human review.
     */
    PROBABLE
}
