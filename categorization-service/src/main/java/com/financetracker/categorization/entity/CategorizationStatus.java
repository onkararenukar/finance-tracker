package com.financetracker.categorization.entity;

/**
 * Whether a transaction's category has been confirmed or is awaiting
 * your review because the LLM suggested a category name that doesn't
 * match anything in the existing {@code categories} table.
 *
 * <p>String-stored enum - see the recurring rationale on every other
 * status enum in this project (StatementStatus, TransactionStatus).
 */
public enum CategorizationStatus {

    /** category_id is set - the LLM's suggestion matched (or you manually confirmed) an existing category. */
    CATEGORIZED,

    /** No existing category matched; suggested_category_name holds the LLM's proposal, awaiting your decision. */
    PENDING_REVIEW
}
