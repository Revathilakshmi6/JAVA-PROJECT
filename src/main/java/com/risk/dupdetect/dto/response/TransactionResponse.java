package com.risk.dupdetect.dto.response;

import com.risk.dupdetect.domain.Transaction;
import com.risk.dupdetect.domain.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for transaction responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private UUID id;
    private String payerId;
    private String payeeId;
    private BigDecimal amount;
    private String currency;
    private String idempotencyRef;
    private TransactionStatus status;
    private Instant createdAt;

    public static TransactionResponse fromEntity(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .payerId(transaction.getPayerId())
                .payeeId(transaction.getPayeeId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .idempotencyRef(transaction.getIdempotencyRef())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
