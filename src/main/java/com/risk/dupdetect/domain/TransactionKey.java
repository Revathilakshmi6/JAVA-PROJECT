package com.risk.dupdetect.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable composite key for transaction identity.
 * Normalizes amount scale and implements compareTo-based equality
 * combined with stripped-trailing-zeros hashing to avoid the BigDecimal scale trap.
 */
public final class TransactionKey {
    private final String payerId;
    private final String payeeId;
    private final BigDecimal amount;
    private final String currency;
    private final String idempotencyRef;

    public TransactionKey(String payerId, String payeeId, BigDecimal amount, String currency, String idempotencyRef) {
        this.payerId = payerId;
        this.payeeId = payeeId;
        // Normalize amount to scale 2 with HALF_EVEN rounding
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_EVEN) : null;
        this.currency = currency != null ? currency.toUpperCase() : null;
        this.idempotencyRef = idempotencyRef;
    }

    public String getPayerId() {
        return payerId;
    }

    public String getPayeeId() {
        return payeeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyRef() {
        return idempotencyRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionKey that = (TransactionKey) o;

        // Use compareTo for scale-insensitive BigDecimal comparison
        boolean amountsEqual = (this.amount == null && that.amount == null) ||
                (this.amount != null && that.amount != null && this.amount.compareTo(that.amount) == 0);

        return Objects.equals(payerId, that.payerId) &&
               Objects.equals(payeeId, that.payeeId) &&
               amountsEqual &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(idempotencyRef, that.idempotencyRef);
    }

    @Override
    public int hashCode() {
        // Strip trailing zeros for consistency with compareTo-based equality
        BigDecimal hashedAmount = amount != null ? amount.stripTrailingZeros() : null;
        return Objects.hash(payerId, payeeId, hashedAmount, currency, idempotencyRef);
    }

    @Override
    public String toString() {
        return "TransactionKey{" +
                "payerId='" + payerId + '\'' +
                ", payeeId='" + payeeId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", idempotencyRef='" + idempotencyRef + '\'' +
                '}';
    }
}
