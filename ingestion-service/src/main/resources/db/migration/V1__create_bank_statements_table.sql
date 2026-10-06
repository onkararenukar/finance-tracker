-- ============================================================================
-- V1__create_bank_statements_table.sql
--
-- Flyway migration #1 for the ingestion-service.
--
-- Flyway naming convention: V<version>__<description>.sql
--   - The double underscore is required.
--   - Flyway runs these in ascending version order, exactly once each,
--     and records what's been applied in the `flyway_schema_history`
--     table it creates automatically. Never edit an already-applied
--     migration file - add a new V2__... file instead, or Flyway will
--     detect a checksum mismatch and refuse to start.
--
-- WHAT THIS TABLE IS FOR:
-- One row per uploaded bank statement file. This is the "source of
-- truth" for what's been uploaded and how far it's progressed through
-- the pipeline (ingested -> parsed -> categorized). The actual
-- transaction line-items live in a separate `transactions` table
-- owned by the parsing-service's migrations (not this one) - keeping
-- each service's migrations scoped to the tables it owns.
-- ============================================================================

-- Enable pgvector's `vector` type in case it wasn't already enabled by
-- the docker init script (e.g. when running against a pre-existing DB).
-- IF NOT EXISTS makes this safe to re-run / idempotent.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE bank_statements (
    id                  BIGSERIAL PRIMARY KEY,

    -- Which bank this statement came from. Free text initially rather
    -- than a foreign key to a `banks` table - with multiple banks and
    -- inconsistent naming on statements, we let the LLM parsing step
    -- normalize this later rather than forcing rigid enum values here.
    bank_name           VARCHAR(120) NOT NULL,

    -- Original filename as uploaded, kept for display/debugging.
    original_filename   VARCHAR(255) NOT NULL,

    -- 'PDF' or 'CSV' - drives which extraction strategy is used.
    file_type           VARCHAR(10)  NOT NULL,

    -- Where the raw file bytes are stored (e.g. a local path or S3/MinIO
    -- key). We deliberately do NOT store the file bytes themselves in
    -- Postgres - keeps the DB small and fast to back up.
    storage_path         VARCHAR(500) NOT NULL,

    -- SHA-256 hash of the file content. Lets us detect and reject
    -- duplicate uploads of the exact same statement (a user re-uploading
    -- the same PDF twice) without a slow byte-by-byte comparison.
    file_checksum        VARCHAR(64)  NOT NULL,

    -- Pipeline status - tracks progress through the async, event-driven
    -- flow. Always store enums as STRING, never ORDINAL: ordinal values
    -- silently break if enum constants are ever reordered.
    --   UPLOADED         -> file received & stored, not yet processed
    --   TEXT_EXTRACTED    -> raw text/rows pulled out, ready for Kafka publish
    --   PUBLISHED         -> statement.ingested event sent to Kafka
    --   PARSING_FAILED     -> extraction or publish failed (see failure_reason)
    status                VARCHAR(30)  NOT NULL DEFAULT 'UPLOADED',

    -- Populated only when status = PARSING_FAILED, for debugging/support.
    failure_reason        TEXT,

    -- How many raw text chunks this statement was split into once
    -- extracted (mirrors the "chunk -> per row" step in the design).
    -- Nullable until extraction completes.
    chunk_count            INTEGER,

    -- Optimistic locking - prevents lost updates if two processes
    -- (e.g. a retry job and the original request) touch the same row
    -- concurrently. Hibernate auto-increments this on every UPDATE and
    -- rejects the write with an exception if the version has moved on.
    version                 BIGINT NOT NULL DEFAULT 0,

    -- Always TIMESTAMPTZ (timestamp WITH time zone), never bare
    -- TIMESTAMP - avoids ambiguity about which timezone a timestamp is
    -- in once the app or its users span multiple timezones.
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Every status-filtered query (e.g. "find all UPLOADED statements to
-- retry") hits this index instead of scanning the whole table.
CREATE INDEX idx_bank_statements_status ON bank_statements(status);

-- Used by the dashboard's "retrieval of chunks based on day/week" step
-- to quickly range-scan statements by upload date.
CREATE INDEX idx_bank_statements_created_at ON bank_statements(created_at);

-- Enforces the duplicate-upload rejection described above at the DB
-- level (belt-and-suspenders alongside the application-level check).
CREATE UNIQUE INDEX idx_bank_statements_checksum ON bank_statements(file_checksum);

-- Partial index: most queries only care about statements that are
-- still mid-pipeline, not the (eventually much larger) set of fully
-- completed ones. A partial index keeps this fast as the table grows.
CREATE INDEX idx_bank_statements_in_progress ON bank_statements(status)
    WHERE status IN ('UPLOADED', 'TEXT_EXTRACTED');
