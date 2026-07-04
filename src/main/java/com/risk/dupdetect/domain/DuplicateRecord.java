package com.risk.dupdetect.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity mapping for the duplicate_record database table.
 */
@Entity
@Table(name = "duplicate_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DuplicateRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_txn_id", nullable = false)
    private Transaction originalTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "duplicate_txn_id", nullable = false)
    private Transaction duplicateTransaction;

    @Column(name = "match_tier", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MatchTier matchTier;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "matched_fields", columnDefinition = "TEXT")
    private List<String> matchedFields;

    @Column(name = "time_delta_ms", nullable = false)
    private long timeDeltaMs;

    @Column(name = "resolution", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ResolutionStatus resolution;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
        if (resolution == null) {
            resolution = ResolutionStatus.PENDING;
        }
    }
}
