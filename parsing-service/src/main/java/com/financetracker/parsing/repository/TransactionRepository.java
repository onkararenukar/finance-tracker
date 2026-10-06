package com.financetracker.parsing.repository;

import com.financetracker.parsing.entity.Transaction;
import com.financetracker.parsing.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Powers a retry job for transactions whose embedding step failed -
     * paginated, never an unbounded list (see the efficiency checklist:
     * "small today" doesn't stay small).
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = :status ORDER BY t.createdAt ASC")
    Page<Transaction> findByStatus(@Param("status") TransactionStatus status, Pageable pageable);
}
