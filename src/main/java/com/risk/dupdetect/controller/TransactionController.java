package com.risk.dupdetect.controller;

import com.risk.dupdetect.domain.Transaction;
import com.risk.dupdetect.domain.TransactionStatus;
import com.risk.dupdetect.dto.request.TransactionRequest;
import com.risk.dupdetect.dto.response.TransactionResponse;
import com.risk.dupdetect.dto.response.WindowStatusResponse;
import com.risk.dupdetect.service.DuplicateDetectionService;
import com.risk.dupdetect.service.SlidingWindowStore;
import com.risk.dupdetect.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import java.util.UUID;

/**
 * REST controller for transaction ingestion and retrieval.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final DuplicateDetectionService duplicateDetectionService;
    private final TransactionRepository transactionRepository;
    private final SlidingWindowStore slidingWindowStore;
    private final Environment environment;

    @Autowired
    public TransactionController(
            DuplicateDetectionService duplicateDetectionService,
            TransactionRepository transactionRepository,
            SlidingWindowStore slidingWindowStore,
            Environment environment) {
        this.duplicateDetectionService = duplicateDetectionService;
        this.transactionRepository = transactionRepository;
        this.slidingWindowStore = slidingWindowStore;
        this.environment = environment;
    }

    /**
     * Ingests a new payment transaction.
     */
    @PostMapping
    @RateLimiter(name = "transactionIngestion")
    public ResponseEntity<TransactionResponse> submitTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
        
        Transaction result = duplicateDetectionService.processTransaction(request, idempotencyHeader);
        TransactionResponse response = TransactionResponse.fromEntity(result);
        
        switch (result.getStatus()) {
            case FLAGGED:
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            case SUPPRESSED:
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            case POSTED:
            default:
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }

    /**
     * Retrieves a single transaction by its UUID.
     */
    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable UUID id) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        return ResponseEntity.ok(TransactionResponse.fromEntity(txn));
    }

    /**
     * Queries transactions with pagination, optionally filtered by payerId and status.
     */
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @RequestParam(required = false) String payerId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Transaction> resultPage;

        if (payerId != null && status != null) {
            resultPage = transactionRepository.findByPayerIdAndStatus(payerId, status, pageable);
        } else if (payerId != null) {
            resultPage = transactionRepository.findByPayerId(payerId, pageable);
        } else if (status != null) {
            resultPage = transactionRepository.findByStatus(status, pageable);
        } else {
            resultPage = transactionRepository.findAll(pageable);
        }

        return ResponseEntity.ok(resultPage.map(TransactionResponse::fromEntity));
    }

    /**
     * Retrieves current health/status of the sliding window store.
     */
    @GetMapping("/window/status")
    public ResponseEntity<WindowStatusResponse> getWindowStatus() {
        String storeType = environment.getProperty("transaction.store.type", "inmemory");
        long windowSecs = environment.getProperty("transaction.window.seconds", Long.class, 60L);
        WindowStatusResponse response = WindowStatusResponse.builder()
                .size(slidingWindowStore.size())
                .oldestEntryAgeSeconds(slidingWindowStore.getOldestEntryAgeSeconds())
                .storeType(storeType)
                .windowSeconds(windowSecs)
                .build();
        return ResponseEntity.ok(response);
    }
}
