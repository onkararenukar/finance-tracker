package com.financetracker.ingestion.service;

import com.financetracker.ingestion.dto.ExtractedChunk;
import com.financetracker.ingestion.dto.event.StatementIngestedEvent;
import com.financetracker.ingestion.entity.BankStatement;
import com.financetracker.ingestion.entity.StatementStatus;
import com.financetracker.ingestion.exception.DuplicateStatementException;
import com.financetracker.ingestion.repository.BankStatementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The main orchestration service for the ingestion pipeline. Given an
 * uploaded file, this class is responsible, end to end, for:
 *
 * <ol>
 *   <li>Rejecting duplicate uploads (by content checksum)</li>
 *   <li>Persisting the raw file to disk (storage_path)</li>
 *   <li>Creating the {@link BankStatement} tracking row (status=UPLOADED)</li>
 *   <li>Extracting text chunks via {@link TextExtractionService}</li>
 *   <li>Publishing a {@link StatementIngestedEvent} to Kafka</li>
 *   <li>Advancing the row's status as each step completes</li>
 * </ol>
 *
 * <p>Every step updates {@code status} so that if the process crashes
 * partway through (e.g. the JVM dies after step 3 but before step 5), the
 * row's status accurately reflects exactly how far it got, and a retry
 * job (using {@code BankStatementRepository.findByStatus}) knows precisely
 * where to resume.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatementIngestionService {

    private final BankStatementRepository repository;
    private final TextExtractionService textExtractionService;
    private final KafkaTemplate<String, StatementIngestedEvent> kafkaTemplate;

    @Value("${finance-tracker.kafka.topics.statement-ingested}")
    private String statementIngestedTopic;

    /** Where uploaded files are persisted. In production, point this at an S3/MinIO-backed path instead. */
    @Value("${finance-tracker.storage.upload-dir:/tmp/finance-tracker/uploads}")
    private String uploadDir;

    /**
     * Handles one statement upload end-to-end.
     *
     * <p>Deliberately NOT wrapped in a single {@code @Transactional} block
     * spanning the whole method. Each {@code repository.save(...)} call
     * commits independently (Spring Data's default per-call transaction),
     * so the row's status is durably visible after every step. We never
     * want one long-lived transaction held open across the Kafka network
     * call in the middle - that would pin a DB connection-pool slot and
     * row locks for the entire duration of a call to a different system,
     * which under load is exactly how a slow Kafka broker takes down
     * your database connection pool too.
     */
    public BankStatement ingest(MultipartFile file, String bankName, Long userId) {
        byte[] content = readBytes(file);
        String checksum = sha256Hex(content);

        // Fail fast BEFORE writing anything to disk if we've seen this
        // exact file before - avoids wasted disk I/O for a request we're
        // going to reject anyway.
        repository.findByFileChecksum(checksum).ifPresent(existing -> {
            throw new DuplicateStatementException(
                    "This statement has already been uploaded (statement id=" + existing.getId() + ")");
        });

        String fileType = resolveFileType(file.getOriginalFilename());
        Path storedPath = persistToDisk(content, fileType);

        BankStatement statement = new BankStatement(
                bankName,
                file.getOriginalFilename(),
                fileType,
                storedPath.toString(),
                checksum,
                userId
        );
        statement = repository.save(statement);
        log.info("Stored new bank statement id={} bank={} file={} user={}",
                statement.getId(), bankName, file.getOriginalFilename(), userId);

        try {
            List<ExtractedChunk> chunks = textExtractionService.extract(storedPath, fileType);
            statement.setChunkCount(chunks.size());
            statement.setStatus(StatementStatus.TEXT_EXTRACTED);
            statement = repository.save(statement);

            publishIngestedEvent(statement, chunks);

            statement.setStatus(StatementStatus.PUBLISHED);
            return repository.save(statement);

        } catch (RuntimeException ex) {
            // Record the failure on the row itself instead of just
            // throwing - this is what lets a support engineer (or you,
            // debugging at 1am) query `SELECT * FROM bank_statements
            // WHERE status = 'PARSING_FAILED'` and see exactly why each
            // one failed, rather than having to dig through log files.
            log.error("Failed to process statement id={}", statement.getId(), ex);
            statement.setStatus(StatementStatus.PARSING_FAILED);
            statement.setFailureReason(ex.getMessage());
            repository.save(statement);
            throw ex;
        }
    }

    /**
     * Publishes the {@code statement.ingested} event.
     *
     * <p>The Kafka message KEY is the statementId (as a string), not
     * left null/random. This matters: Kafka guarantees ordering only
     * within a partition, and the partition a keyed message lands on is
     * a deterministic hash of its key. Keying by statementId means every
     * event for a given statement (today just one, but this leaves room
     * for future multi-event flows per statement) is guaranteed to be
     * processed in order by parsing-service consumers.
     */
    private void publishIngestedEvent(BankStatement statement, List<ExtractedChunk> chunks) {
        var event = new StatementIngestedEvent(
                statement.getId(),
                statement.getBankName(),
                statement.getFileType(),
                chunks,
                Instant.now()
        );

        kafkaTemplate.send(statementIngestedTopic, String.valueOf(statement.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed for statement id={}", statement.getId(), ex);
                    } else {
                        log.info("Published statement.ingested event for statement id={} to partition={}",
                                statement.getId(), result.getRecordMetadata().partition());
                    }
                });
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file bytes", e);
        }
    }

    /** SHA-256 hex digest, used both to reject duplicate uploads and as a content-addressed filename. */
    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM - this branch is unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Determines PDF vs CSV from the filename extension.
     *
     * <p>Uses Java 24's finalized pattern matching for {@code switch} on
     * a plain string suffix check - simple here, but this is the same
     * pattern-matching machinery that makes {@code TextExtractionService}'s
     * dispatch exhaustive and compiler-checked.
     */
    private String resolveFileType(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Uploaded file has no filename");
        }
        String lower = filename.toLowerCase();
        return switch (lower) {
            case String s when s.endsWith(".pdf") -> "PDF";
            case String s when s.endsWith(".csv") -> "CSV";
            default -> throw new IllegalArgumentException(
                    "Unsupported file extension for: " + filename + " (expected .pdf or .csv)");
        };
    }

    /** Writes the file to a content-addressed path (checksum-based name avoids collisions). */
    private Path persistToDisk(byte[] content, String fileType) {
        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            String extension = fileType.equals("PDF") ? "pdf" : "csv";
            Path target = dir.resolve(UUID.randomUUID() + "." + extension);
            Files.write(target, content);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist uploaded statement to disk", e);
        }
    }
}
