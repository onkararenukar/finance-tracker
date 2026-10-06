-- ============================================================================
-- V1__create_users_table.sql
--
-- Flyway migration for API Gateway - User Management and RBAC
--
-- This migration creates the users table with role-based access control
-- support for the finance tracker application.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    
    -- User authentication
    username            VARCHAR(50)  NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL, -- bcrypt hash
    
    -- User profile
    full_name           VARCHAR(100),
    
    -- Role-based access control
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER',
    -- Valid roles: USER, ADMIN
    
    -- Account status
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    is_verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    
    -- Security fields
    failed_login_attempts INTEGER     NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    password_changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Timestamps
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_is_active ON users(is_active);

-- Insert default admin user (password: admin123 - should be changed immediately)
-- In production, this should be done through a secure initialization process
INSERT INTO users (username, email, password_hash, full_name, role, is_active, is_verified)
VALUES (
    'admin',
    'admin@financetracker.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- bcrypt hash for 'admin123'
    'System Administrator',
    'ADMIN',
    TRUE,
    TRUE
);

-- Create a function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to automatically update updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
