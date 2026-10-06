package com.financetracker.parsing.entity;

/**
 * Tracks a {@link Transaction}'s progress through the remainder of the
 * pipeline after the LLM has extracted it from a statement chunk.
 *
 * <p>Stored as a string (see {@code @Enumerated(EnumType.STRING)} on the
 * entity) - never ordinal - for the same reordering-safety reason
 * documented on ingestion-service's {@code StatementStatus}.
 */
public enum TransactionStatus {

    /** Row created from the LLM's structured extraction; embedding not yet computed. */
    PARSED,

    /** Embedding computed and stored in transaction_embeddings. */
    EMBEDDED,

    /** The transaction.parsed Kafka event was successfully published. */
    PUBLISHED,

    /** Embedding computation failed - see logs; row stays here for a retry job to find. */
    EMBEDDING_FAILED
}
