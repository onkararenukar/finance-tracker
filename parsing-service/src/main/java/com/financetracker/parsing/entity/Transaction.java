package com.financetracker.parsing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity mapping to the {@code transactions} table
 * ({@code V1__create_transactions_table.sql}).
 *
 * <p>Deliberately does NOT map the {@code transaction_embeddings} table's
 * vector column as a JPA relationship/field. See
 * {@link com.financetracker.parsing.repository.TransactionEmbeddingStore}
 * for why embeddings are written via plain JDBC instead of Hibernate.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_statement_id", columnList = "statement_id"),
                @Index(name = "idx_transactions_date", columnList = "transaction_date"),
                @Index(name = "idx_transactions_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    /** User ID for multi-tenant support - associates transactions with specific users */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_chunk_index", nullable = false)
    private Integer sourceChunkIndex;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * Always {@link BigDecimal}, never double/float - monetary values
     * must not be subject to floating-point rounding error. Mapped to
     * NUMERIC(14,2) in the migration.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "llm_model", nullable = false, length = 100)
    private String llmModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status = TransactionStatus.PARSED;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Transaction(Long statementId, Integer sourceChunkIndex, String bankName,
                        LocalDate transactionDate, String description, BigDecimal amount,
                        TransactionDirection direction, String sourceText, String llmModel, Long userId) {
        this.statementId = statementId;
        this.sourceChunkIndex = sourceChunkIndex;
        this.bankName = bankName;
        this.transactionDate = transactionDate;
        this.description = description;
        this.amount = amount;
        this.direction = direction;
        this.sourceText = sourceText;
        this.llmModel = llmModel;
        this.userId = userId;
    }
}
