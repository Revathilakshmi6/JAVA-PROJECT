package com.risk.dupdetect.repository;

import com.risk.dupdetect.domain.DuplicateRecord;
import com.risk.dupdetect.domain.MatchTier;
import com.risk.dupdetect.domain.ResolutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA Repository for DuplicateRecord entities.
 */
@Repository
public interface DuplicateRecordRepository extends JpaRepository<DuplicateRecord, UUID> {

    Page<DuplicateRecord> findByMatchTierAndResolution(MatchTier matchTier, ResolutionStatus resolution, Pageable pageable);

    Page<DuplicateRecord> findByMatchTier(MatchTier matchTier, Pageable pageable);

    Page<DuplicateRecord> findByResolution(ResolutionStatus resolution, Pageable pageable);
}
