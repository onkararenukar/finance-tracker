-- ============================================================================
-- V2__create_transaction_categories_table.sql
--
-- One row per transaction (owned by parsing-service, referenced only by
-- id - no cross-service FK, same principle as transaction_embeddings in
-- parsing-service). Tracks which category a transaction was assigned,
-- OR, if the LLM proposed a category name that doesn't exist yet, the
-- pending suggestion awaiting your review.
-- ============================================================================

CREATE TABLE transaction_categories (
    transaction_id          BIGINT PRIMARY KEY,

    -- NULL while status = PENDING_REVIEW (no confirmed category yet).
    category_id               BIGINT REFERENCES categories(id),

    -- STRING enum, never ORDINAL (see the recurring rationale across
    -- every other status enum in this project).
    --   CATEGORIZED     -> category_id is set, LLM's suggestion matched
    --                      an existing category
    --   PENDING_REVIEW   -> the LLM proposed a category name with no
    --                       existing match; suggested_category_name is
    --                       set, category_id is NULL, waiting on you
    status                     VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',

    -- The LLM's raw suggested category name, ALWAYS stored regardless of
    -- whether it matched an existing category or not - useful both for
    -- display ("the LLM suggested X, you confirmed Y instead") and for
    -- debugging categorization quality over time.
    suggested_category_name    VARCHAR(80) NOT NULL,

    -- One-line description the LLM generated for this transaction, per
    -- the original design notes ("output will contain what the
    -- transaction was about... give it a category and short one line
    -- description").
    one_line_description        TEXT NOT NULL,

    llm_model                    VARCHAR(100) NOT NULL,

    version                       BIGINT NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_categories_status ON transaction_categories(status);
CREATE INDEX idx_transaction_categories_category_id ON transaction_categories(category_id);

-- Powers the dashboard's pending-review queue - the single most
-- frequently run query against this table, so it gets a dedicated
-- partial index rather than relying on the general status index alone.
CREATE INDEX idx_transaction_categories_pending
    ON transaction_categories(created_at)
    WHERE status = 'PENDING_REVIEW';
