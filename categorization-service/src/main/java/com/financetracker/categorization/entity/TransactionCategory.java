package com.financetracker.categorization.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for {@code transaction_categories}. Uses {@code transactionId}
 * as its OWN primary key (a natural key, not a synthetic one) - since
 * there's exactly one categorization record per transaction, a separate
 * auto-generated id would just be redundant surrogate state to keep in
 * sync with something that's already unique.
 *
 * <p>{@code categoryId} is a plain {@code Long} rather than a
 * {@code @ManyToOne Category} relationship - deliberately avoiding lazy-
 * loading semantics here since the only thing ever needed from a
 * "linked" category in this service's own code is its id or name,
 * both of which are cheap to look up explicitly via
 * {@code CategoryRepository} when actually needed (e.g. building an API
 * response). This keeps the entity simple to reason about and avoids
 * accidental N+1 queries from an unguarded relationship traversal.
 */
@Entity
@Table(name = "transaction_categories")
@Getter
@Setter
@NoArgsConstructor
public class TransactionCategory {

    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategorizationStatus status = CategorizationStatus.PENDING_REVIEW;

    @Column(name = "suggested_category_name", nullable = false, length = 80)
    private String suggestedCategoryName;

    @Column(name = "one_line_description", nullable = false, columnDefinition = "TEXT")
    private String oneLineDescription;

    @Column(name = "llm_model", nullable = false, length = 100)
    private String llmModel;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TransactionCategory(Long transactionId, Long categoryId, CategorizationStatus status,
                                String suggestedCategoryName, String oneLineDescription, String llmModel) {
        this.transactionId = transactionId;
        this.categoryId = categoryId;
        this.status = status;
        this.suggestedCategoryName = suggestedCategoryName;
        this.oneLineDescription = oneLineDescription;
        this.llmModel = llmModel;
    }
}
