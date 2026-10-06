-- ============================================================================
-- V2__create_transaction_embeddings_table.sql
--
-- A SEPARATE table from `transactions` (rather than an `embedding` column
-- bolted onto it) for two reasons:
--   1. Embeddings are wide (thousands of floats) and rarely need to be
--      read alongside the transaction row itself in normal dashboard
--      queries - keeping them separate means everyday `SELECT * FROM
--      transactions` queries stay fast and don't drag megabytes of
--      vector data across the wire for no reason.
--   2. It lets us swap embedding models later (re-embed everything with
--      a new model into a differently-dimensioned column/table) without
--      touching the transactions table at all.
-- ============================================================================

CREATE TABLE transaction_embeddings (
    transaction_id    BIGINT PRIMARY KEY REFERENCES transactions(id) ON DELETE CASCADE,

    -- 1536 dimensions matches common embedding models (e.g.
    -- text-embedding-3-small). If you swap to a model with a different
    -- output size, this column's dimension must change too - pgvector
    -- enforces the declared dimension at insert time.
    embedding           VECTOR(1536) NOT NULL,

    embedding_model       VARCHAR(100) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW (Hierarchical Navigable Small World) index for fast approximate
-- nearest-neighbor search. This is what makes the analytics-service's
-- future "find transactions similar to X" / RAG-style retrieval queries
-- fast on tens of thousands of rows instead of doing a full sequential
-- scan comparing every embedding.
--
-- vector_cosine_ops: we compare embeddings using cosine distance (`<=>`
-- operator), the standard choice for text-embedding models, which
-- compares vector DIRECTION rather than magnitude.
CREATE INDEX idx_transaction_embeddings_hnsw
    ON transaction_embeddings
    USING hnsw (embedding vector_cosine_ops);
