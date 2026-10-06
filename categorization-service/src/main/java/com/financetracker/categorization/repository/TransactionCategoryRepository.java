package com.financetracker.categorization.repository;

import com.financetracker.categorization.entity.CategorizationStatus;
import com.financetracker.categorization.entity.TransactionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, Long> {

    /** Powers the dashboard's pending-review queue (the pinkish-red-highlighted list). Always paginated. */
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.status = :status ORDER BY tc.createdAt DESC")
    Page<TransactionCategory> findByStatus(@Param("status") CategorizationStatus status, Pageable pageable);
}
