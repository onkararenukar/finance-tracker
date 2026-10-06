package com.financetracker.ingestion.service;

import com.financetracker.ingestion.dto.ExtractedChunk;
import com.financetracker.ingestion.exception.UnsupportedStatementFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Pulls raw, unstructured text out of an uploaded bank statement file and
 * splits it into bounded {@link ExtractedChunk}s ready to be handed to the
 * (future) parsing-service via Kafka.
 *
 * <p>This service deliberately does NO understanding of the content -
 * it doesn't know what a "transaction" is or try to parse amounts/dates.
 * That intelligence belongs to the LLM-driven parsing-service downstream.
 * Ingestion's only job is: bytes in, ordered text chunks out.
 * 
 * <p>Now supports both text-based PDFs and scanned PDFs via OCR.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TextExtractionService {

    private final OcrService ocrService;

    /**
     * How many CSV rows (or lines of PDF text) go into a single chunk.
     * Kept small so each chunk maps to a reasonably small LLM prompt
     * downstream - large enough to give the LLM useful surrounding
     * context per call, small enough to keep prompts cheap and fast.
     */
    private static final int ROWS_PER_CHUNK = 25;

    /**
     * Extracts ordered text chunks from a statement file.
     *
     * <p>Uses pattern matching for {@code switch} to dispatch on file type with
     * exhaustiveness the compiler can check, instead of an if/else chain
     * or a switch on strings with no compile-time safety.
     *
     * <p>PDFs are processed with OCR support for scanned documents,
     * while CSVs are parsed with Apache Commons CSV.
     *
     * @param filePath the on-disk location of the previously-stored upload
     * @param fileType "PDF" or "CSV" (validated by the caller)
     * @return ordered list of extracted chunks, ready for Kafka publish
     */
    public List<ExtractedChunk> extract(Path filePath, String fileType) {
        return switch (fileType.toUpperCase()) {
            case "PDF" -> extractFromPdf(filePath);
            case "CSV" -> extractFromCsv(filePath);
            default -> throw new UnsupportedStatementFormatException(
                    "Unsupported statement file type: " + fileType + ". Only PDF and CSV are supported.");
        };
    }

    /**
     * Extracts text page-by-page from a PDF, then re-chunks the combined
     * text into fixed-size line windows so chunk size stays predictable
     * regardless of how a given bank formats its PDF pages.
     * 
     * Now supports both text-based PDFs and scanned PDFs via OCR.
     */
    private List<ExtractedChunk> extractFromPdf(Path filePath) {
        try {
            log.info("Extracting text from PDF: {}", filePath.getFileName());
            
            // Use OCR service which handles both text-based and scanned PDFs
            String fullText = ocrService.extractTextFromPdf(filePath);
            
            log.info("Extracted {} characters from PDF", fullText.length());

            // Split into individual lines, then group into fixed-size windows
            // Using Java 21 compatible approach instead of Gatherers.windowFixed
            List<String> lines = fullText.lines()
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toList());

            log.info("Split into {} non-empty lines", lines.size());

            List<List<String>> lineWindows = partitionList(lines, ROWS_PER_CHUNK);

            log.info("Created {} chunks ({} lines per chunk)", lineWindows.size(), ROWS_PER_CHUNK);

            return toChunks(lineWindows);
        } catch (IOException e) {
            log.error("Failed to extract text from PDF statement: {}", filePath.getFileName(), e);
            throw new UnsupportedStatementFormatException(
                    "Failed to extract text from PDF statement: " + filePath.getFileName(), e);
        }
    }

    /**
     * Extracts rows from a CSV, re-serializing each fixed-size window of
     * rows back into a plain-text block (rather than raw CSVRecord objects)
     * so both PDF- and CSV-derived chunks share the same "plain text"
     * shape by the time they reach the LLM parsing step downstream.
     */
    private List<ExtractedChunk> extractFromCsv(Path filePath) {
        try (var reader = new InputStreamReader(Files.newInputStream(filePath), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            String headerLine = String.join(",", headers);

            List<String> rows = parser.stream()
                    .map(this::renderCsvRecord)
                    .collect(Collectors.toList());

            List<List<String>> rowWindows = partitionList(rows, ROWS_PER_CHUNK);

            // Prepend the header line to every window's rendered text so
            // each self-contained chunk still tells the LLM what each
            // column means, even though only the first physical window
            // originally had the header.
            return Stream.iterate(0, i -> i < rowWindows.size(), i -> i + 1)
                    .map(i -> new ExtractedChunk(
                            i,
                            headerLine + "\n" + String.join("\n", rowWindows.get(i))))
                    .toList();

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read CSV statement: " + filePath.getFileName(), e);
        }
    }

    private String renderCsvRecord(CSVRecord record) {
        return String.join(",", record.toList());
    }

    /**
     * Partitions a list into fixed-size sublists.
     * Java 21 compatible replacement for Gatherers.windowFixed
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /** Converts fixed-size line windows into indexed, joined-text {@link ExtractedChunk}s. */
    private List<ExtractedChunk> toChunks(List<List<String>> windows) {
        return IntStream.range(0, windows.size())
                .mapToObj(i -> new ExtractedChunk(i, String.join("\n", windows.get(i))))
                .collect(Collectors.toList());
    }
}