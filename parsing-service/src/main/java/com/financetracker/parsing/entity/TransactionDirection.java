package com.financetracker.parsing.entity;

/**
 * Whether a transaction moved money into the account (CREDIT/income) or
 * out of it (DEBIT/expense).
 *
 * <p>Kept separate from the sign of the {@code amount} column
 * deliberately - {@code amount} is always stored as a positive
 * magnitude, and this enum carries the direction. This avoids an entire
 * class of bugs where summing signed amounts silently produces the
 * wrong total if a negative sign gets dropped or doubled somewhere
 * along the pipeline (e.g. a naive {@code Math.abs()} call).
 */
public enum TransactionDirection {
    CREDIT,
    DEBIT
}
