package com.risk.dupdetect.controller;

import com.risk.dupdetect.domain.DuplicateRecord;
import com.risk.dupdetect.domain.MatchTier;
import com.risk.dupdetect.domain.ResolutionStatus;
import com.risk.dupdetect.domain.Transaction;
import com.risk.dupdetect.domain.TransactionStatus;
import com.risk.dupdetect.dto.request.DuplicateResolutionRequest;
import com.risk.dupdetect.dto.response.DuplicateRecordResponse;
import com.risk.dupdetect.repository.DuplicateRecordRepository;
import com.risk.dupdetect.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for managing duplicate records and queues.
 */
@RestController
@RequestMapping("/api/v1/duplicates")
public class DuplicateController {

    private final DuplicateRecordRepository duplicateRecordRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public DuplicateController(
            DuplicateRecordRepository duplicateRecordRepository,
            TransactionRepository transactionRepository) {
        this.duplicateRecordRepository = duplicateRecordRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Lists duplicate records with pagination and optional filters.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<DuplicateRecordResponse>> getDuplicates(
            @RequestParam(required = false) MatchTier tier,
            @RequestParam(required = false) ResolutionStatus resolution,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("detectedAt").descending());
        Page<DuplicateRecord> resultPage;

        if (tier != null && resolution != null) {
            resultPage = duplicateRecordRepository.findByMatchTierAndResolution(tier, resolution, pageable);
        } else if (tier != null) {
            resultPage = duplicateRecordRepository.findByMatchTier(tier, pageable);
        } else if (resolution != null) {
            resultPage = duplicateRecordRepository.findByResolution(resolution, pageable);
        } else {
            resultPage = duplicateRecordRepository.findAll(pageable);
        }

        return ResponseEntity.ok(resultPage.map(DuplicateRecordResponse::fromEntity));
    }

    /**
     * Fetches details of a specific duplicate record.
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<DuplicateRecordResponse> getDuplicateById(@PathVariable UUID id) {
        DuplicateRecord record = duplicateRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duplicate record not found"));
        return ResponseEntity.ok(DuplicateRecordResponse.fromEntity(record));
    }

    /**
     * Resolves a flagged probable duplicate.
     */
    @PostMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<DuplicateRecordResponse> resolveDuplicate(
            @PathVariable UUID id,
            @Valid @RequestBody DuplicateResolutionRequest request) {

        DuplicateRecord record = duplicateRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duplicate record not found"));

        if (record.getMatchTier() == MatchTier.EXACT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exact duplicate records cannot be manually resolved");
        }

        if (record.getResolution() != ResolutionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate record has already been resolved");
        }

        record.setResolution(request.getResolution());
        record.setResolvedBy("ANALYST"); // Hardcoded default analyst for now; will be updated with security principal
        record.setResolvedAt(Instant.now());

        // If the analyst rules that it is NOT a duplicate (rejection of duplicate flag), 
        // we update the transaction status from FLAGGED to POSTED.
        if (request.getResolution() == ResolutionStatus.REJECTED) {
            Transaction txn = record.getDuplicateTransaction();
            txn.setStatus(TransactionStatus.POSTED);
            transactionRepository.save(txn);
        }

        duplicateRecordRepository.save(record);
        return ResponseEntity.ok(DuplicateRecordResponse.fromEntity(record));
    }
}
