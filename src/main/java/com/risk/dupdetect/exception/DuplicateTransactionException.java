package com.risk.dupdetect.exception;

import java.util.UUID;

/**
 * Exception thrown when an exact duplicate transaction is detected.
 */
public class DuplicateTransactionException extends RuntimeException {
    private final UUID transactionId;

    public DuplicateTransactionException(String message, UUID transactionId) {
        super(message);
        this.transactionId = transactionId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }
}
