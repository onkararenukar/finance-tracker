package com.financetracker.parsing.repository;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Reads and writes the {@code transaction_embeddings} table directly via
 * JDBC, deliberately bypassing Hibernate/JPA for this one table.
 *
 * <p><b>Why not just map this as a JPA entity like everything else?</b>
 * pgvector's {@code vector} column type isn't one of Hibernate's built-in
 * types. Getting Hibernate to understand it correctly requires either a
 * hand-written custom {@code UserType}/{@code JdbcType} implementation or
 * pinning to a specific newer Hibernate version with built-in vector
 * support - both add real complexity and version fragility for a single
 * table. Going through the pgvector-java library's {@link PGvector}
 * class directly over a plain JDBC {@code PreparedStatement} is simple,
 * well-documented by pgvector's own maintainers, and has no Hibernate
 * version coupling at all. The trade-off: this class doesn't get
 * Hibernate's dirty-checking/caching for free, but a
 * write-once-then-read-only-for-similarity-search access pattern doesn't
 * benefit from those anyway.
 */
@Repository
@RequiredArgsConstructor
public class TransactionEmbeddingStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Upserts the embedding for a given transaction.
     *
     * <p>{@code PGvector.addVectorType(connection)} registers pgvector's
     * custom type with the JDBC connection so the driver knows how to
     * serialize a {@code float[]} into the wire format Postgres expects
     * for a {@code vector} column - without this call, {@code setObject}
     * would fail with a "type not found" style error.
     */
    public void save(Long transactionId, float[] embedding, String embeddingModel) {
        jdbcTemplate.execute((Connection connection) -> {
            try {
                PGvector.addVectorType(connection);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to register pgvector type on connection", e);
            }
            try (var pstmt = connection.prepareStatement("""
                    INSERT INTO transaction_embeddings (transaction_id, embedding, embedding_model)
                    VALUES (?, ?, ?)
                    ON CONFLICT (transaction_id)
                    DO UPDATE SET embedding = EXCLUDED.embedding,
                                  embedding_model = EXCLUDED.embedding_model,
                                  created_at = NOW()
                    """)) {
                pstmt.setLong(1, transactionId);
                pstmt.setObject(2, new PGvector(embedding));
                pstmt.setString(3, embeddingModel);
                pstmt.executeUpdate();
                return null;
            }
        });
    }

    /**
     * Finds the {@code limit} transactions whose embeddings are most
     * similar (by cosine distance) to the given query vector.
     *
     * <p>Not used by this service today, but this is exactly the query
     * the future analytics-service will run for RAG-style retrieval -
     * e.g. "pull the chunks/transactions most relevant to building this
     * week's spending summary prompt", matching the "retrieval of chunks
     * based on day or week" step from the original design notes. Defined
     * here, next to the write path, so the query shape stays obviously
     * in sync with how embeddings are actually stored.
     *
     * <p>The {@code <=>} operator is pgvector's cosine DISTANCE operator
     * (0 = identical direction, 2 = opposite) - we order ascending
     * because smaller distance means more similar.
     */
    public java.util.List<Long> findMostSimilarTransactionIds(float[] queryEmbedding, int limit) {
        return jdbcTemplate.query(
                connection -> {
                    PGvector.addVectorType(connection);
                    var pstmt = connection.prepareStatement("""
                            SELECT transaction_id
                            FROM transaction_embeddings
                            ORDER BY embedding <=> ?
                            LIMIT ?
                            """);
                    pstmt.setObject(1, new PGvector(queryEmbedding));
                    pstmt.setInt(2, limit);
                    return pstmt;
                },
                (rs, rowNum) -> rs.getLong("transaction_id")
        );
    }
}
