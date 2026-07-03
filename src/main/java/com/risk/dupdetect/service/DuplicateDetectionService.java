package com.risk.dupdetect.service;

import com.risk.dupdetect.domain.*;
import com.risk.dupdetect.dto.request.TransactionRequest;
import com.risk.dupdetect.dto.response.DuplicateRecordResponse;
import com.risk.dupdetect.exception.DuplicateTransactionException;
import com.risk.dupdetect.repository.DuplicateRecordRepository;
import com.risk.dupdetect.repository.TransactionRepository;
import com.risk.dupdetect.websocket.DuplicateAlertBroadcaster;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Service to handle the logic of duplicate transaction detection.
 */
@Service
@Slf4j
public class DuplicateDetectionService {

    private final SlidingWindowStore slidingWindowStore;
    private final TransactionRepository transactionRepository;
    private final DuplicateRecordRepository duplicateRecordRepository;
    private final SystemConfigService configService;
    private final BloomFilterService bloomFilterService;
    private final DuplicateAlertBroadcaster broadcaster;

    @Autowired
    public DuplicateDetectionService(
            SlidingWindowStore slidingWindowStore,
            TransactionRepository transactionRepository,
            DuplicateRecordRepository duplicateRecordRepository,
            SystemConfigService configService,
            BloomFilterService bloomFilterService,
            DuplicateAlertBroadcaster broadcaster) {
        this.slidingWindowStore = slidingWindowStore;
        this.transactionRepository = transactionRepository;
        this.duplicateRecordRepository = duplicateRecordRepository;
        this.configService = configService;
        this.bloomFilterService = bloomFilterService;
        this.broadcaster = broadcaster;
    }

    /**
     * Processes an incoming transaction.
     * Evaluates against Tier-1 exact duplicate rules.
     *
     * @param request           the transaction request payload
     * @param idempotencyHeader the idempotency header from client request
     * @return the saved transaction if new, or throws DuplicateTransactionException if duplicate
     */
    @Transactional(noRollbackFor = DuplicateTransactionException.class)
    public Transaction processTransaction(TransactionRequest request, String idempotencyHeader) {
        // Resolve idempotency reference
        String idempotencyRef = idempotencyHeader;
        if (idempotencyRef == null || idempotencyRef.trim().isEmpty()) {
            idempotencyRef = request.getIdempotencyRef();
        }
        if (idempotencyRef == null || idempotencyRef.trim().isEmpty()) {
            idempotencyRef = generatePayloadHash(request);
        }

        BigDecimal normalizedAmount = request.getAmount().setScale(2, RoundingMode.HALF_EVEN);
        TransactionKey key = new TransactionKey(
                request.getPayerId(),
                request.getPayeeId(),
                normalizedAmount,
                request.getCurrency(),
                idempotencyRef
        );

        log.info("Ingesting transaction with key: {}", key);

        // Bloom filter fast pre-check: if bloom says "no", it's definitely new.
        // We still need to record it in both stores to match future duplicates.
        boolean mightBeDuplicate = bloomFilterService.mightContain(key);
        boolean isNew = true;
        if (mightBeDuplicate) {
            isNew = slidingWindowStore.addIfAbsent(key);
        } else {
            slidingWindowStore.addIfAbsent(key);
            bloomFilterService.put(key);
        }

        if (isNew && mightBeDuplicate) {
            bloomFilterService.put(key);
        }

        if (!isNew) {
            log.warn("Tier-1 Exact duplicate detected: {}", key);

            // Persist the suppressed transaction for audit
            Transaction duplicateTxn = Transaction.builder()
                    .payerId(request.getPayerId())
                    .payeeId(request.getPayeeId())
                    .amount(normalizedAmount)
                    .currency(request.getCurrency().toUpperCase())
                    .idempotencyRef(idempotencyRef)
                    .status(TransactionStatus.SUPPRESSED)
                    .build();
            duplicateTxn = transactionRepository.save(duplicateTxn);

            // Query for the original transaction record within the window
            Instant since = Instant.now().minusSeconds(configService.getWindowSeconds());
            List<Transaction> candidates = transactionRepository.findPotentialDuplicates(
                    request.getPayerId(),
                    request.getPayeeId(),
                    normalizedAmount,
                    request.getCurrency().toUpperCase(),
                    since
            );

            Transaction originalTxn = null;
            for (Transaction candidate : candidates) {
                if (idempotencyRef.equals(candidate.getIdempotencyRef()) && 
                    candidate.getStatus() == TransactionStatus.POSTED) {
                    originalTxn = candidate;
                    break;
                }
            }

            long timeDeltaMs = 0;
            if (originalTxn != null) {
                timeDeltaMs = java.time.Duration.between(originalTxn.getCreatedAt(), duplicateTxn.getCreatedAt()).toMillis();
            }

            // Save duplicate record audit log
            List<String> matchedFields = List.of("payerId", "payeeId", "amount", "currency", "idempotencyRef");
            DuplicateRecord record = DuplicateRecord.builder()
                    .originalTransaction(originalTxn != null ? originalTxn : duplicateTxn)
                    .duplicateTransaction(duplicateTxn)
                    .matchTier(MatchTier.EXACT)
                    .matchedFields(matchedFields)
                    .timeDeltaMs(timeDeltaMs)
                    .resolution(ResolutionStatus.APPROVED) // auto-approved suppression
                    .resolvedBy("SYSTEM")
                    .resolvedAt(Instant.now())
                    .build();
            duplicateRecordRepository.save(record);

            broadcaster.broadcast(DuplicateRecordResponse.fromEntity(record));

            throw new DuplicateTransactionException("Exact duplicate transaction detected and auto-suppressed", duplicateTxn.getId());
        }

        // Check for probable duplicate (same payer, payee, amount, currency, but different reference)
        Instant since = Instant.now().minusSeconds(configService.getWindowSeconds());
        List<Transaction> candidates = transactionRepository.findPotentialDuplicates(
                request.getPayerId(),
                request.getPayeeId(),
                normalizedAmount,
                request.getCurrency().toUpperCase(),
                since
        );

        // Find the earliest POSTED transaction to treat as the original
        Transaction originalTxn = null;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            Transaction c = candidates.get(i);
            if (c.getStatus() == TransactionStatus.POSTED) {
                originalTxn = c;
                break;
            }
        }

        if (originalTxn != null) {
            // Found a posted transaction within the window with same details but different reference!
            // Flag this transaction as a probable duplicate.
            Transaction flaggedTxn = Transaction.builder()
                    .payerId(request.getPayerId())
                    .payeeId(request.getPayeeId())
                    .amount(normalizedAmount)
                    .currency(request.getCurrency().toUpperCase())
                    .idempotencyRef(idempotencyRef)
                    .status(TransactionStatus.FLAGGED)
                    .build();
            flaggedTxn = transactionRepository.save(flaggedTxn);

            long timeDeltaMs = java.time.Duration.between(originalTxn.getCreatedAt(), flaggedTxn.getCreatedAt()).toMillis();
            List<String> matchedFields = List.of("payerId", "payeeId", "amount", "currency");

            DuplicateRecord record = DuplicateRecord.builder()
                    .originalTransaction(originalTxn)
                    .duplicateTransaction(flaggedTxn)
                    .matchTier(MatchTier.PROBABLE)
                    .matchedFields(matchedFields)
                    .timeDeltaMs(timeDeltaMs)
                    .resolution(ResolutionStatus.PENDING)
                    .build();
            duplicateRecordRepository.save(record);

            broadcaster.broadcast(DuplicateRecordResponse.fromEntity(record));

            log.info("Tier-2 Probable duplicate flagged: {}", key);
            return flaggedTxn;
        }

        return savePostedTransaction(request, normalizedAmount, idempotencyRef);
    }

    private Transaction savePostedTransaction(TransactionRequest request, BigDecimal normalizedAmount, String idempotencyRef) {
        Transaction txn = Transaction.builder()
                .payerId(request.getPayerId())
                .payeeId(request.getPayeeId())
                .amount(normalizedAmount)
                .currency(request.getCurrency().toUpperCase())
                .idempotencyRef(idempotencyRef)
                .status(TransactionStatus.POSTED)
                .build();
        return transactionRepository.save(txn);
    }

    private String generatePayloadHash(TransactionRequest request) {
        String payload = String.format("%s:%s:%s:%s",
                request.getPayerId(),
                request.getPayeeId(),
                request.getAmount().setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                request.getCurrency().toUpperCase()
        );
        return Hashing.sha256().hashString(payload, StandardCharsets.UTF_8).toString();
    }
}
