package com.financetracker.ingestion.entity;

/**
 * Tracks a {@link BankStatement}'s progress through the ingestion pipeline.
 *
 * <p>Stored in Postgres as a {@code VARCHAR} (via {@code @Enumerated(EnumType.STRING)}
 * on the entity) rather than an ordinal integer. This is deliberate: if this
 * enum is ever reordered or a new value inserted in the middle, ordinal
 * storage would silently corrupt every existing row's meaning, whereas
 * string storage is immune to reordering and is human-readable when you're
 * debugging with {@code psql} directly.
 */
public enum StatementStatus {

    /** File has been received and its bytes stored, nothing else done yet. */
    UPLOADED,

    /** Raw text/rows have been extracted from the PDF/CSV and chunked. */
    TEXT_EXTRACTED,

    /** The {@code statement.ingested} Kafka event was successfully published. */
    PUBLISHED,

    /** Extraction or publishing failed - see {@code failureReason} on the entity. */
    PARSING_FAILED
}
