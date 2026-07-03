package com.risk.dupdetect.repository;

import com.risk.dupdetect.domain.Transaction;
import com.risk.dupdetect.domain.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for Transaction entities.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    Page<Transaction> findByPayerIdAndStatus(String payerId, TransactionStatus status, Pageable pageable);
    
    Page<Transaction> findByPayerId(String payerId, Pageable pageable);
    
    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    /**
     * Finds potential matches within the sliding window from the database.
     */
    @Query("SELECT t FROM Transaction t WHERE t.payerId = :payerId AND t.payeeId = :payeeId " +
           "AND t.amount = :amount AND t.currency = :currency AND t.createdAt >= :since " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findPotentialDuplicates(
            @Param("payerId") String payerId,
            @Param("payeeId") String payeeId,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("since") Instant since
    );
}
