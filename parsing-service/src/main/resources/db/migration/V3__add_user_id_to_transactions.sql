-- ============================================================================
-- V3__add_user_id_to_transactions.sql
--
-- This migration adds user_id to transactions table for multi-tenant support
-- ============================================================================

-- Add user_id column (nullable initially for backward compatibility)
ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- Create index for user-based queries (will be built after data migration)
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);

-- Add composite index for user + date queries
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, transaction_date);

-- Add composite index for user + status queries
CREATE INDEX IF NOT EXISTS idx_transactions_user_status ON transactions(user_id, status);
