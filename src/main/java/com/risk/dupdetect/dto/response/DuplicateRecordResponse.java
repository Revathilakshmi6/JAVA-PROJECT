package com.risk.dupdetect.dto.response;

import com.risk.dupdetect.domain.DuplicateRecord;
import com.risk.dupdetect.domain.MatchTier;
import com.risk.dupdetect.domain.ResolutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO representing detailed duplicate transaction logs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuplicateRecordResponse {
    private UUID id;
    private TransactionResponse originalTransaction;
    private TransactionResponse duplicateTransaction;
    private MatchTier matchTier;
    private List<String> matchedFields;
    private long timeDeltaMs;
    private ResolutionStatus resolution;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant detectedAt;

    public static DuplicateRecordResponse fromEntity(DuplicateRecord record) {
        return DuplicateRecordResponse.builder()
                .id(record.getId())
                .originalTransaction(TransactionResponse.fromEntity(record.getOriginalTransaction()))
                .duplicateTransaction(TransactionResponse.fromEntity(record.getDuplicateTransaction()))
                .matchTier(record.getMatchTier())
                .matchedFields(record.getMatchedFields())
                .timeDeltaMs(record.getTimeDeltaMs())
                .resolution(record.getResolution())
                .resolvedBy(record.getResolvedBy())
                .resolvedAt(record.getResolvedAt())
                .detectedAt(record.getDetectedAt())
                .build();
    }
}
