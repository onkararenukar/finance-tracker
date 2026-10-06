package com.financetracker.analytics.repository;

import com.financetracker.analytics.dto.CategoryBreakdownResponse;
import com.financetracker.analytics.dto.SummaryResponse;
import com.financetracker.analytics.dto.TimeseriesPointResponse;
import com.financetracker.analytics.dto.TransactionListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * All read-only aggregation SQL lives in this ONE class - see pom.xml's
 * javadoc for why a plain JDBC read layer (rather than JPA entities) is
 * the right shape for a service that owns no tables of its own.
 *
 * <p>Every query LEFT JOINs {@code transaction_categories} (never INNER
 * JOINs) - a transaction that hasn't been categorized yet (or is still
 * PENDING_REVIEW) must still show up in totals and lists, just bucketed
 * under "Uncategorized" via {@code COALESCE}. An INNER JOIN would make
 * freshly-parsed transactions invisible on the dashboard until
 * categorization-service finishes with them, which would make the
 * numbers look wrong rather than just incomplete.
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SummaryResponse getSummary(LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS total_income,
                    COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0) AS total_expense
                FROM transactions
                WHERE transaction_date BETWEEN :from AND :to
                """;
        var params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);

        return jdbc.queryForObject(sql, params, (rs, rowNum) -> {
            BigDecimal income = rs.getBigDecimal("total_income");
            BigDecimal expense = rs.getBigDecimal("total_expense");
            return new SummaryResponse(income, expense, income.subtract(expense));
        });
    }

    public List<CategoryBreakdownResponse> getCategoryBreakdown(LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(c.name, 'Uncategorized') AS category_name,
                    t.direction,
                    SUM(t.amount) AS total,
                    COUNT(*) AS transaction_count
                FROM transactions t
                LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id
                LEFT JOIN categories c ON c.id = tc.category_id
                WHERE t.transaction_date BETWEEN :from AND :to
                GROUP BY COALESCE(c.name, 'Uncategorized'), t.direction
                ORDER BY total DESC
                """;
        var params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);

        return jdbc.query(sql, params, (rs, rowNum) -> new CategoryBreakdownResponse(
                rs.getString("category_name"),
                rs.getString("direction"),
                rs.getBigDecimal("total"),
                rs.getLong("transaction_count")
        ));
    }

    /**
     * @param granularity "day", "week", or "month" - validated by the
     *                    controller before reaching here, then used
     *                    directly as a Postgres {@code date_trunc} field
     *                    name. See {@code AnalyticsController} for the
     *                    validation that makes this safe from SQL
     *                    injection despite the string concatenation below
     *                    (date_trunc's field argument can't be
     *                    parameterized as a bind variable in Postgres,
     *                    only a literal - hence the allowlist check
     *                    happening one layer up instead).
     */
    public List<TimeseriesPointResponse> getTimeseries(LocalDate from, LocalDate to, String granularity) {
        String sql = """
                SELECT
                    date_trunc('%s', transaction_date)::date AS bucket_start,
                    COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS income,
                    COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0) AS expense
                FROM transactions
                WHERE transaction_date BETWEEN :from AND :to
                GROUP BY bucket_start
                ORDER BY bucket_start ASC
                """.formatted(granularity);
        var params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);

        return jdbc.query(sql, params, (rs, rowNum) -> new TimeseriesPointResponse(
                rs.getObject("bucket_start", LocalDate.class),
                rs.getBigDecimal("income"),
                rs.getBigDecimal("expense")
        ));
    }

    public List<TransactionListItemResponse> getTransactions(LocalDate from, LocalDate to, Long categoryId,
                                                               int limit, int offset) {
        String sql = """
                SELECT
                    t.id AS transaction_id,
                    t.transaction_date,
                    t.bank_name,
                    t.description,
                    t.amount,
                    t.direction,
                    COALESCE(c.name, 'Uncategorized') AS category_name,
                    (tc.status = 'PENDING_REVIEW' OR tc.status IS NULL) AS is_pending_review,
                    tc.suggested_category_name
                FROM transactions t
                LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id
                LEFT JOIN categories c ON c.id = tc.category_id
                WHERE t.transaction_date BETWEEN :from AND :to
                  AND (:categoryId::bigint IS NULL OR c.id = :categoryId)
                ORDER BY t.transaction_date DESC, t.id DESC
                LIMIT :limit OFFSET :offset
                """;
        var params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("categoryId", categoryId)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(sql, params, (rs, rowNum) -> new TransactionListItemResponse(
                rs.getLong("transaction_id"),
                rs.getObject("transaction_date", LocalDate.class),
                rs.getString("bank_name"),
                rs.getString("description"),
                rs.getBigDecimal("amount"),
                rs.getString("direction"),
                rs.getString("category_name"),
                rs.getBoolean("is_pending_review"),
                rs.getString("suggested_category_name")
        ));
    }
}
