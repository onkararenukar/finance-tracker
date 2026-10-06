package com.financetracker.smsagent.service;

import com.financetracker.smsagent.model.BankMessage;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a batch of {@link BankMessage}s into ingestion-service uploads.
 *
 * <p><b>Key design choice:</b> rather than adding a new "SMS upload"
 * endpoint to ingestion-service, we group messages by detected bank and
 * serialize each group as an in-memory CSV (date, sender, body) that we
 * upload through the exact same {@code POST /api/v1/statements} endpoint
 * already built for PDF/CSV statements. Ingestion-service's
 * {@code TextExtractionService} already knows how to chunk CSV rows, and
 * parsing-service's LLM prompt already knows how to extract transactions
 * from raw statement-like text - an SMS batch formatted as CSV rows is
 * indistinguishable from a bank-exported CSV statement by the time it
 * reaches the LLM. This means the entire rest of the pipeline (Kafka
 * events, chunking, parsing, embedding, and now categorization) needed
 * ZERO changes to support this new data source.
 */
public class IngestionUploader {

    private static final Logger log = LoggerFactory.getLogger(IngestionUploader.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final String ingestionServiceUrl;
    private final String defaultBankName;
    private final BankMessageFilter bankMessageFilter;
    private final OkHttpClient httpClient;

    public IngestionUploader(String ingestionServiceUrl, String defaultBankName, BankMessageFilter bankMessageFilter) {
        this.ingestionServiceUrl = ingestionServiceUrl;
        this.defaultBankName = defaultBankName;
        this.bankMessageFilter = bankMessageFilter;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Groups the given messages by detected bank, uploading one CSV per
     * group. Returns {@code true} only if EVERY group uploaded
     * successfully - the caller (see {@code SmsSyncScheduler}) uses this
     * to decide whether it's safe to advance the sync checkpoint. If any
     * group fails, we deliberately do NOT advance the checkpoint, so the
     * next cycle retries the entire batch rather than silently losing
     * messages whose upload failed (e.g. because ingestion-service was
     * briefly down).
     */
    public boolean uploadBatch(List<BankMessage> messages) {
        if (messages.isEmpty()) {
            return true;
        }

        Map<String, List<BankMessage>> byBank = messages.stream()
                .collect(Collectors.groupingBy(m -> bankMessageFilter.detectBankName(m.sender()).orElse(defaultBankName)));

        boolean allSucceeded = true;
        for (var entry : byBank.entrySet()) {
            boolean success = uploadCsv(entry.getKey(), toCsv(entry.getValue()));
            allSucceeded &= success;
        }
        return allSucceeded;
    }

    private byte[] toCsv(List<BankMessage> messages) {
        StringBuilder sb = new StringBuilder("date,sender,body\n");
        for (BankMessage m : messages) {
            sb.append(escapeCsv(m.sentAt().toString())).append(',')
                    .append(escapeCsv(m.sender() == null ? "" : m.sender())).append(',')
                    .append(escapeCsv(m.body())).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Minimal CSV field escaping: wrap in quotes and double up any embedded quotes if the field needs it. */
    private String escapeCsv(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * Returns true on a successful (2xx) upload. Note that if a retry
     * re-sends an IDENTICAL batch (same messages, checkpoint didn't
     * advance because a sibling group failed last cycle),
     * ingestion-service's existing checksum-based duplicate detection
     * naturally rejects it with 409 Conflict rather than double-counting
     * transactions - we treat 409 as "fine, already handled" too.
     */
    private boolean uploadCsv(String bankName, byte[] csvBytes) {
        String filename = "sms-batch-%s.csv".formatted(TIMESTAMP_FORMAT.format(java.time.Instant.now()));

        RequestBody fileBody = RequestBody.create(csvBytes, MediaType.parse("text/csv"));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .addFormDataPart("bankName", bankName)
                .build();

        Request request = new Request.Builder()
                .url(ingestionServiceUrl + "/api/v1/statements")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 409) {
                log.info("Batch for bank={} already ingested (duplicate checksum) - treating as success", bankName);
                return true;
            }
            if (!response.isSuccessful()) {
                log.error("Upload failed for bank={} status={} body={}",
                        bankName, response.code(), response.body() != null ? response.body().string() : "");
                return false;
            }
            log.info("Uploaded SMS batch for bank={} (HTTP {})", bankName, response.code());
            return true;
        } catch (IOException e) {
            log.error("Upload failed for bank={} - ingestion-service unreachable at {}",
                    bankName, ingestionServiceUrl, e);
            return false;
        }
    }
}
