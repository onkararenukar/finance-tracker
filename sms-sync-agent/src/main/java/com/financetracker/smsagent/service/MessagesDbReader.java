package com.financetracker.smsagent.service;

import com.financetracker.smsagent.model.BankMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads new incoming messages from the Messages app's SQLite database
 * ({@code chat.db}), starting after a given ROWID high-water mark.
 *
 * <h2>Two macOS-specific gotchas this class handles</h2>
 * <ol>
 *   <li><b>Full Disk Access.</b> If the process running this JVM hasn't
 *       been granted Full Disk Access (System Settings → Privacy &amp;
 *       Security → Full Disk Access → add your terminal app or
 *       {@code java} binary), SQLite will fail to open the file with an
 *       opaque "unable to open database file" error. We check readability
 *       up front and throw a message that actually explains this,
 *       instead of letting that cryptic JDBC error surface.</li>
 *   <li><b>Apple's timestamp epoch.</b> The {@code message.date} column
 *       is NOT a Unix timestamp. Since macOS Sierra (2016), it's stored
 *       as NANOSECONDS since 2001-01-01T00:00:00Z ("Mac absolute time" /
 *       Core Data epoch); older macOS versions stored SECONDS since that
 *       same epoch. We detect which based on magnitude - a value this
 *       large can only be nanoseconds, never seconds (see {@code
 *       toInstant}) - and convert accordingly. Getting this wrong
 *       silently produces dates decades off, which is a nasty bug to
 *       spot after the fact.</li>
 * </ol>
 */
public class MessagesDbReader {

    private static final Logger log = LoggerFactory.getLogger(MessagesDbReader.class);

    /** 2001-01-01T00:00:00Z - the epoch Apple's Core Data / Messages timestamps are relative to. */
    private static final Instant APPLE_EPOCH = Instant.parse("2001-01-01T00:00:00Z");

    /**
     * Threshold used to distinguish old-format (seconds) from new-format
     * (nanoseconds) timestamps: a nanosecond value for any date after
     * ~2001 is always larger than 10^12, while a seconds value for any
     * realistic date is always smaller than that.
     */
    private static final long NANOSECOND_THRESHOLD = 1_000_000_000_000L;

    private final Path chatDbPath;

    public MessagesDbReader(Path chatDbPath) {
        this.chatDbPath = chatDbPath;
    }

    /**
     * Returns every incoming (not sent-by-you) message with ROWID greater
     * than {@code afterRowId}, ordered oldest-first so callers process
     * (and checkpoint) them in a consistent, resumable order.
     */
    public List<BankMessage> readNewMessages(long afterRowId) throws SQLException {
        verifyReadable();

        // Read-only connection: this agent must never write to a live
        // Messages database - a read-write open could corrupt state
        // Messages.app itself depends on.
        String url = "jdbc:sqlite:" + chatDbPath.toAbsolutePath() + "?mode=ro";

        List<BankMessage> results = new ArrayList<>();
        String sql = """
                SELECT m.ROWID   AS row_id,
                       m.date    AS raw_date,
                       m.text    AS body,
                       h.id      AS sender
                FROM message m
                LEFT JOIN handle h ON m.handle_id = h.ROWID
                WHERE m.ROWID > ?
                  AND m.is_from_me = 0
                  AND m.text IS NOT NULL
                ORDER BY m.ROWID ASC
                """;

        try (Connection conn = DriverManager.getConnection(url);
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, afterRowId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new BankMessage(
                            rs.getLong("row_id"),
                            toInstant(rs.getLong("raw_date")),
                            rs.getString("sender"),
                            rs.getString("body")
                    ));
                }
            }
        }

        log.info("Read {} new message(s) from chat.db after rowId={}", results.size(), afterRowId);
        return results;
    }

    private Instant toInstant(long rawDate) {
        if (rawDate > NANOSECOND_THRESHOLD) {
            return APPLE_EPOCH.plusNanos(rawDate);
        }
        return APPLE_EPOCH.plusSeconds(rawDate);
    }

    private void verifyReadable() {
        if (!Files.isReadable(chatDbPath)) {
            throw new IllegalStateException("""
                    Cannot read %s

                    This is almost always a macOS Full Disk Access permission issue, not a \
                    missing-file issue. Fix: System Settings -> Privacy & Security -> Full Disk \
                    Access -> enable it for the app/terminal you're running this agent from \
                    (e.g. Terminal.app, iTerm, or your IDE), then restart that app completely.
                    """.formatted(chatDbPath));
        }
    }
}
