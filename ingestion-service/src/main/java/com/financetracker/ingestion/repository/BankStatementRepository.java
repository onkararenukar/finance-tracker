package com.financetracker.ingestion.repository;

import com.financetracker.ingestion.entity.BankStatement;
import com.financetracker.ingestion.entity.StatementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link BankStatement}.
 *
 * <p>Spring generates the implementation of every method here at
 * startup by parsing the method name / {@code @Query} - no hand-written
 * SQL/JDBC boilerplate needed.
 */
public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    /**
     * Used at upload time to reject duplicate files before doing any
     * expensive extraction work. Backed by the unique index on
     * {@code file_checksum} at the DB level too (belt-and-suspenders).
     */
    Optional<BankStatement> findByFileChecksum(String fileChecksum);

    /**
     * Powers a retry/recovery job: find statements stuck in an
     * in-progress state (e.g. the app crashed mid-processing) so they
     * can be re-attempted. Always paginated - never return an unbounded
     * list, even for what's expected to be a small result set today;
     * "small today" has a way of not staying small.
     */
    @Query("SELECT s FROM BankStatement s WHERE s.status = :status ORDER BY s.createdAt ASC")
    Page<BankStatement> findByStatus(@Param("status") StatementStatus status, Pageable pageable);
}
