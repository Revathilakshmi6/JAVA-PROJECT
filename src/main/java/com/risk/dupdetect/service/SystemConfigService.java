package com.risk.dupdetect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage dynamic system configurations like sliding window duration,
 * identity fields, and amount tolerances.
 */
@Service
public class SystemConfigService {

    @Value("${transaction.window.seconds:60}")
    private volatile long windowSeconds = 60;

    private final Set<String> identityFields = ConcurrentHashMap.newKeySet();

    private volatile BigDecimal amountTolerance = BigDecimal.ZERO;

    public SystemConfigService() {
        // Default identity fields
        identityFields.add("payerId");
        identityFields.add("payeeId");
        identityFields.add("amount");
        identityFields.add("currency");
        identityFields.add("idempotencyRef");
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Set<String> getIdentityFields() {
        return Collections.unmodifiableSet(identityFields);
    }

    public void setIdentityFields(Set<String> fields) {
        this.identityFields.clear();
        this.identityFields.addAll(fields);
    }

    public BigDecimal getAmountTolerance() {
        return amountTolerance;
    }

    public void setAmountTolerance(BigDecimal amountTolerance) {
        this.amountTolerance = amountTolerance;
    }
}
