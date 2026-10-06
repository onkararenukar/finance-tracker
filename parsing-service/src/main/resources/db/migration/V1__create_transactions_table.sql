-- ============================================================================
-- V1__create_transactions_table.sql
--
-- Owned by parsing-service. Holds one row per structured transaction the
-- LLM extracted from a bank statement chunk. This table does NOT include
-- a `category` column - categorization is a separate concern owned by
-- categorization-service, which will add that column in its own
-- migration (V1__add_category_columns.sql, applied against the same
-- physical database - each service's Flyway history table tracks only
-- the migrations IT applied, via a per-service `flyway_schema_history`
-- table name, see application.yml).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE transactions (
    id                   BIGSERIAL PRIMARY KEY,

    -- Which uploaded statement this transaction was extracted from.
    -- No formal FK constraint to ingestion-service's bank_statements
    -- table on purpose: in a microservice architecture, each service
    -- owns its own schema, and a cross-service foreign key would create
    -- a hard coupling (ingestion-service could never evolve its table
    -- independently). We still index it for fast lookups.
    statement_id          BIGINT NOT NULL,

    -- Which chunk (from ExtractedChunk.chunkIndex in ingestion-service)
    -- this transaction was parsed out of - useful for debugging a bad
    -- LLM extraction by tracing it back to the exact source text.
    source_chunk_index     INTEGER NOT NULL,

    bank_name               VARCHAR(120) NOT NULL,

    -- Date of the transaction as it appeared on the statement (not the
    -- date we processed it - that's created_at below).
    transaction_date         DATE NOT NULL,

    -- The LLM's cleaned-up, human-readable description of the transaction
    -- (e.g. "Coffee Shop purchase" rather than a raw merchant code).
    description               TEXT NOT NULL,

    -- Stored as NUMERIC, never FLOAT/DOUBLE - floating point types lose
    -- precision on monetary values (classic 0.1 + 0.2 != 0.3 problem).
    -- NUMERIC(14,2) supports amounts up to ~999 billion with 2 decimal places.
    amount                     NUMERIC(14, 2) NOT NULL,

    -- 'CREDIT' (money in) or 'DEBIT' (money out) - kept separate from the
    -- sign of `amount` so `amount` can always be stored as a positive
    -- magnitude, avoiding sign-convention bugs when summing.
    direction                  VARCHAR(10) NOT NULL,

    -- The exact raw text chunk this transaction was extracted from -
    -- kept for auditability (if the LLM mis-parses something, we can
    -- see exactly what it was looking at) and to let categorization-service
    -- re-embed or re-prompt using the original source text if needed.
    source_text                TEXT NOT NULL,

    -- Which LLM model produced this extraction - useful when comparing
    -- extraction quality across model upgrades, or reprocessing only
    -- transactions parsed by an older/weaker model.
    llm_model                  VARCHAR(100) NOT NULL,

    -- STRING enum, never ORDINAL (see ingestion-service's StatementStatus
    -- for the full rationale).
    --   PARSED             -> row created, embedding not yet computed
    --   EMBEDDED           -> embedding stored in transaction_embeddings
    --   PUBLISHED          -> transaction.parsed Kafka event sent
    --   EMBEDDING_FAILED    -> embedding step failed, event not sent
    status                      VARCHAR(30) NOT NULL DEFAULT 'PARSED',

    version                      BIGINT NOT NULL DEFAULT 0,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Every dashboard query ("this month's transactions", "transactions for
-- statement X") filters by one of these.
CREATE INDEX idx_transactions_statement_id ON transactions(statement_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_status ON transactions(status);

-- Composite index supporting the dashboard's most common query shape:
-- "sum of transactions in this date range, optionally filtered by
-- direction" - a single index serves both the range scan and the filter.
CREATE INDEX idx_transactions_date_direction ON transactions(transaction_date, direction);
