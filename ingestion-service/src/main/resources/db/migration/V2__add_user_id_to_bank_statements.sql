-- ============================================================================
-- V2__add_user_id_to_bank_statements.sql
--
-- This migration adds user_id to bank_statements table for multi-tenant support
-- ============================================================================

-- Add user_id column (nullable initially for backward compatibility)
ALTER TABLE bank_statements 
ADD COLUMN user_id BIGINT;

-- Create index for user-based queries
CREATE INDEX idx_bank_statements_user_id ON bank_statements(user_id);

-- Add composite index for user + status queries
CREATE INDEX idx_bank_statements_user_status ON bank_statements(user_id, status);
