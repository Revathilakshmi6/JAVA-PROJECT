package com.risk.dupdetect.service;

import com.risk.dupdetect.domain.*;
import com.risk.dupdetect.dto.request.TransactionRequest;
import com.risk.dupdetect.exception.DuplicateTransactionException;
import com.risk.dupdetect.repository.DuplicateRecordRepository;
import com.risk.dupdetect.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DuplicateDetectionServiceTest {

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DuplicateRecordRepository duplicateRecordRepository;

    @Autowired
    private BloomFilterService bloomFilterService;

    @Autowired
    private SlidingWindowStore slidingWindowStore;

    @BeforeEach
    public void cleanUp() {
        // Clear databases and in-memory store before each test
        duplicateRecordRepository.deleteAll();
        transactionRepository.deleteAll();
        slidingWindowStore.clear();
        bloomFilterService.reset();
    }

    @Test
    public void testProcessNewTransactionSuccessfully() {
        TransactionRequest request = new TransactionRequest(
                "payer-100",
                "payee-200",
                new BigDecimal("150.50"),
                "USD",
                "unique-idemp-1"
        );

        Transaction result = duplicateDetectionService.processTransaction(request, null);

        assertNotNull(result.getId());
        assertEquals(TransactionStatus.POSTED, result.getStatus());
        assertEquals("payer-100", result.getPayerId());
        assertEquals("payee-200", result.getPayeeId());
        assertEquals(new BigDecimal("150.50"), result.getAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals("unique-idemp-1", result.getIdempotencyRef());
        assertNotNull(result.getCreatedAt());

        // Verify it was persisted
        assertTrue(transactionRepository.findById(result.getId()).isPresent());
    }

    @Test
    public void testProcessExactDuplicateSuppressed() {
        TransactionRequest request1 = new TransactionRequest(
                "payer-100",
                "payee-200",
                new BigDecimal("150.50"),
                "USD",
                "same-idemp"
        );

        // Submit first transaction
        Transaction firstTxn = duplicateDetectionService.processTransaction(request1, null);
        assertEquals(TransactionStatus.POSTED, firstTxn.getStatus());

        // Submit second transaction with exact same details and reference (simulating retry)
        TransactionRequest request2 = new TransactionRequest(
                "payer-100",
                "payee-200",
                new BigDecimal("150.50"),
                "USD",
                "same-idemp"
        );

        DuplicateTransactionException exception = assertThrows(
                DuplicateTransactionException.class,
                () -> duplicateDetectionService.processTransaction(request2, null)
        );

        assertNotNull(exception.getTransactionId());
        assertTrue(exception.getMessage().contains("Exact duplicate"));

        // Verify second transaction was saved as SUPPRESSED in the database
        Transaction duplicateTxn = transactionRepository.findById(exception.getTransactionId())
                .orElseThrow(() -> new AssertionError("Duplicate transaction record not found"));
        assertEquals(TransactionStatus.SUPPRESSED, duplicateTxn.getStatus());

        // Verify duplicate audit record was logged
        List<DuplicateRecord> records = duplicateRecordRepository.findAll();
        assertEquals(1, records.size());
        DuplicateRecord auditRecord = records.get(0);

        assertEquals(MatchTier.EXACT, auditRecord.getMatchTier());
        assertEquals(firstTxn.getId(), auditRecord.getOriginalTransaction().getId());
        assertEquals(duplicateTxn.getId(), auditRecord.getDuplicateTransaction().getId());
        assertEquals(ResolutionStatus.APPROVED, auditRecord.getResolution());
        assertEquals("SYSTEM", auditRecord.getResolvedBy());
        assertTrue(auditRecord.getMatchedFields().containsAll(List.of("payerId", "payeeId", "amount", "currency", "idempotencyRef")));
    }

    @Test
    public void testAutoGeneratePayloadHashIfIdempotencyRefMissing() {
        // Submit request without idempotency reference
        TransactionRequest request1 = new TransactionRequest(
                "payer-500",
                "payee-600",
                new BigDecimal("99.99"),
                "USD",
                null
        );

        Transaction firstTxn = duplicateDetectionService.processTransaction(request1, null);
        assertNotNull(firstTxn.getIdempotencyRef());
        assertFalse(firstTxn.getIdempotencyRef().isEmpty());

        // Submit again without idempotency reference (same details)
        TransactionRequest request2 = new TransactionRequest(
                "payer-500",
                "payee-600",
                new BigDecimal("99.99"),
                "USD",
                null
        );

        DuplicateTransactionException exception = assertThrows(
                DuplicateTransactionException.class,
                () -> duplicateDetectionService.processTransaction(request2, null)
        );
        
        assertNotNull(exception.getTransactionId());
    }
}
