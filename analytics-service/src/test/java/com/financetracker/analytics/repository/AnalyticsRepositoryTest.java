package com.financetracker.analytics.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real aggregation SQL against a real Postgres. The schema
 * created in {@link #setUp} is a deliberately minimal SUBSET of what
 * parsing-service's and categorization-service's actual Flyway
 * migrations create - just enough columns for these queries to run
 * against - rather than depending on those modules' migration files
 * directly, which would create an unwanted test-time coupling between
 * this module and two others.
 */
@Testcontainers
class AnalyticsRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("analytics_test");

    private AnalyticsRepository repository;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        repository = new AnalyticsRepository(jdbc);

        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id BIGSERIAL PRIMARY KEY,
                    transaction_date DATE NOT NULL,
                    bank_name VARCHAR(120) NOT NULL,
                    description TEXT NOT NULL,
                    amount NUMERIC(14,2) NOT NULL,
                    direction VARCHAR(10) NOT NULL
                );
                CREATE TABLE IF NOT EXISTS categories (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(80) NOT NULL
                );
                CREATE TABLE IF NOT EXISTS transaction_categories (
                    transaction_id BIGINT PRIMARY KEY,
                    category_id BIGINT REFERENCES categories(id),
                    status VARCHAR(30) NOT NULL
                );
                """);
        jdbc.getJdbcTemplate().execute(
                "TRUNCATE transactions, categories, transaction_categories RESTART IDENTITY CASCADE");
    }

    @Test
    void getSummary_computesIncomeExpenseAndNet() {
        jdbc.getJdbcTemplate().update(
                "INSERT INTO transactions (transaction_date, bank_name, description, amount, direction) VALUES " +
                        "('2026-01-05','Test Bank','Salary',5000.00,'CREDIT')," +
                        "('2026-01-10','Test Bank','Groceries',150.00,'DEBIT')," +
                        "('2026-01-15','Test Bank','Rent',1200.00,'DEBIT')");

        var summary = repository.getSummary(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(summary.totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("1350.00");
        assertThat(summary.net()).isEqualByComparingTo("3650.00");
    }

    @Test
    void getCategoryBreakdown_groupsUncategorizedTransactionsSeparately() {
        jdbc.getJdbcTemplate().update("INSERT INTO categories (id, name) VALUES (1, 'Groceries')");
        jdbc.getJdbcTemplate().update(
                "INSERT INTO transactions (id, transaction_date, bank_name, description, amount, direction) VALUES " +
                        "(1,'2026-01-05','Test Bank','Milk',50.00,'DEBIT')," +
                        "(2,'2026-01-06','Test Bank','Unknown thing',20.00,'DEBIT')");
        jdbc.getJdbcTemplate().update(
                "INSERT INTO transaction_categories (transaction_id, category_id, status) VALUES (1, 1, 'CATEGORIZED')");
        // transaction id=2 deliberately has NO transaction_categories row -
        // simulating a freshly-parsed transaction categorization-service hasn't processed yet.

        var breakdown = repository.getCategoryBreakdown(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown).anySatisfy(row -> {
            assertThat(row.categoryName()).isEqualTo("Groceries");
            assertThat(row.total()).isEqualByComparingTo("50.00");
        });
        assertThat(breakdown).anySatisfy(row -> {
            assertThat(row.categoryName()).isEqualTo("Uncategorized");
            assertThat(row.total()).isEqualByComparingTo("20.00");
        });
    }
}
