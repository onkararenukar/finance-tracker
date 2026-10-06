package com.financetracker.ingestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity mapping 1:1 to the {@code bank_statements} table created by
 * {@code V1__create_bank_statements_table.sql}.
 *
 * <p>Represents a single uploaded bank statement file and where it stands
 * in the ingestion pipeline (uploaded -> text extracted -> published to
 * Kafka). This entity does NOT hold parsed transaction line-items - those
 * belong to the parsing-service's own {@code transactions} table, joined
 * back to this row only by {@code id} (a "statement_id" foreign key lives
 * on that table, owned by that service's migrations).
 *
 * <p>Kept intentionally "thin" - no business logic lives on the entity
 * itself (no rich domain methods); all pipeline transitions are handled
 * in {@code StatementIngestionService} so behavior stays easy to unit test
 * without spinning up JPA/Hibernate.
 */
@Entity
@Table(
        name = "bank_statements",
        indexes = {
                @Index(name = "idx_bank_statements_status", columnList = "status"),
                @Index(name = "idx_bank_statements_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** "PDF" or "CSV" - drives which {@code TextExtractionService} strategy runs. */
    @Column(name = "file_type", nullable = false, length = 10)
    private String fileType;

    /** Where the raw file bytes live on disk/object storage - never the bytes themselves. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** SHA-256 hex digest of the file content, used to reject duplicate uploads. */
    @Column(name = "file_checksum", nullable = false, length = 64, unique = true)
    private String fileChecksum;

    /** User ID for multi-tenant support - associates statements with specific users */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // STRING, never ORDINAL - see StatementStatus javadoc for why.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatementStatus status = StatementStatus.UPLOADED;

    /** Populated only when status = PARSING_FAILED, for support/debugging. */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /** How many text chunks this statement was split into once extracted. */
    @Column(name = "chunk_count")
    private Integer chunkCount;

    /**
     * Optimistic locking column. Hibernate auto-increments this on every
     * UPDATE and throws {@link jakarta.persistence.OptimisticLockException}
     * if a concurrent process modified the row first - prevents silent
     * lost updates without needing a pessimistic (SELECT ... FOR UPDATE) lock.
     */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Convenience constructor for the common "just uploaded" case - the
     * rest of the fields (status, timestamps, version) get their defaults
     * from field initializers / Hibernate annotations.
     */
    public BankStatement(String bankName, String originalFilename, String fileType,
                          String storagePath, String fileChecksum, Long userId) {
        this.bankName = bankName;
        this.originalFilename = originalFilename;
        this.fileType = fileType;
        this.storagePath = storagePath;
        this.fileChecksum = fileChecksum;
        this.userId = userId;
    }
}
