-- ============================================================================
-- V1__create_categories_table.sql
--
-- The master list of categories a transaction can be assigned to. Seeded
-- with a sensible starter set below - you can add more later via a
-- normal INSERT (or a future "add category" admin endpoint); nothing
-- about this table's design assumes the seed list is exhaustive or fixed.
-- ============================================================================

CREATE TABLE categories (
    id           BIGSERIAL PRIMARY KEY,

    -- Case-INsensitive uniqueness is enforced via the functional unique
    -- index below (LOWER(name)), not a plain UNIQUE constraint on name -
    -- otherwise "Groceries" and "groceries" could both be inserted as
    -- "different" categories, which is exactly the kind of duplicate the
    -- LLM-suggestion matching logic in CategorizationService needs to
    -- avoid creating.
    name          VARCHAR(80) NOT NULL,

    -- Whether this category was part of the initial seed list (true) or
    -- added later by you confirming a "new category" suggestion (false).
    -- Purely informational / for future UI grouping - no logic depends on it.
    is_seeded      BOOLEAN NOT NULL DEFAULT false,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_categories_name_lower ON categories (LOWER(name));

INSERT INTO categories (name, is_seeded) VALUES
    ('Groceries', true),
    ('Food & Dining', true),
    ('Transport', true),
    ('Shopping', true),
    ('Bills & Utilities', true),
    ('Entertainment', true),
    ('Health & Wellness', true),
    ('Rent & Housing', true),
    ('Travel', true),
    ('Salary / Income', true),
    ('Transfers', true),
    ('Investments', true),
    ('Fees & Charges', true),
    ('Others', true);
