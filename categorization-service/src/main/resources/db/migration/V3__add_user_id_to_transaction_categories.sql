-- ============================================================================
-- V3__add_user_id_to_transaction_categories.sql
--
-- This migration adds user_id to transaction_categories table for multi-tenant support
-- ============================================================================

-- Add user_id column (no foreign key since users table is owned by api-gateway)
ALTER TABLE transaction_categories 
ADD COLUMN user_id BIGINT NOT NULL DEFAULT 1; -- Default to admin user for existing data

-- Create index for user-based queries
CREATE INDEX idx_transaction_categories_user_id ON transaction_categories(user_id);

-- Add composite index for user + status queries
CREATE INDEX idx_transaction_categories_user_status ON transaction_categories(user_id, status);

-- Update the pending review index to include user_id
DROP INDEX IF EXISTS idx_transaction_categories_pending;
CREATE INDEX idx_transaction_categories_user_pending
    ON transaction_categories(user_id, created_at)
    WHERE status = 'PENDING_REVIEW';
