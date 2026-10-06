package com.financetracker.ingestion.service;

import com.financetracker.ingestion.exception.UnsupportedStatementFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TextExtractionService}.
 *
 * <p>Deliberately no Spring context here (no @SpringBootTest) - this
 * class has no Spring dependencies, so a plain JUnit test starts in
 * milliseconds instead of the seconds a full context load costs. Reach
 * for @SpringBootTest only when a test actually needs the container.
 */
class TextExtractionServiceTest {

    private final OcrService mockOcrService = Mockito.mock(OcrService.class);
    private final TextExtractionService service = new TextExtractionService(mockOcrService);

    @Test
    void extract_csvWithFewRows_producesSingleChunkWithHeader(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("statement.csv");
        Files.writeString(csvFile, """
                date,description,amount
                2026-01-01,Coffee Shop,-4.50
                2026-01-02,Salary,3000.00
                """);

        var chunks = service.extract(csvFile, "CSV");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkIndex()).isZero();
        // Header line should be present so the LLM parsing step downstream
        // still knows what each column means for this chunk.
        assertThat(chunks.get(0).rawText()).contains("date,description,amount");
        assertThat(chunks.get(0).rawText()).contains("Coffee Shop");
        assertThat(chunks.get(0).rawText()).contains("Salary");
    }

    @Test
    void extract_unsupportedFileType_throws(@TempDir Path tempDir) throws IOException {
        Path unknownFile = tempDir.resolve("statement.txt");
        Files.writeString(unknownFile, "irrelevant content");

        assertThatThrownBy(() -> service.extract(unknownFile, "TXT"))
                .isInstanceOf(UnsupportedStatementFormatException.class)
                .hasMessageContaining("Unsupported statement file type");
    }

    @Test
    void extract_largeCsv_producesMultipleChunks(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("large_statement.csv");
        StringBuilder csvContent = new StringBuilder("date,description,amount\n");
        // Generate 500 rows to exceed chunk size (4000 chars)
        for (int i = 0; i < 500; i++) {
            csvContent.append("2026-01-").append(i % 30 + 1).append(",Transaction ").append(i).append(",").append((i % 2 == 0 ? "-" : "")).append((i * 10.5)).append("\n");
        }
        Files.writeString(csvFile, csvContent.toString());

        var chunks = service.extract(csvFile, "CSV");

        assertThat(chunks).hasSizeGreaterThan(1);
        // Verify chunks are properly indexed
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
        // Verify all chunks contain the header
        for (var chunk : chunks) {
            assertThat(chunk.rawText()).contains("date,description,amount");
        }
    }

    @Test
    void extract_csvWithEmptyLines_handlesGracefully(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("statement.csv");
        Files.writeString(csvFile, """
                date,description,amount

                2026-01-01,Coffee Shop,-4.50

                2026-01-02,Salary,3000.00

                """);

        var chunks = service.extract(csvFile, "CSV");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).rawText()).contains("Coffee Shop");
        assertThat(chunks.get(0).rawText()).contains("Salary");
    }
}
