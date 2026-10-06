package com.financetracker.smsagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Watches the directory containing {@code chat.db} for changes using
 * {@code fswatch} (a small native CLI backed by macOS's FSEvents API),
 * and invokes a callback the moment a change is detected.
 *
 * <p><b>Why not Java's built-in {@link java.nio.file.WatchService}:</b>
 * on macOS, the JDK ships no native FSEvents-backed implementation of
 * {@code WatchService} - it silently falls back to an internal polling
 * loop (checking every ~10s by default). That means "watching" a file
 * with plain Java on Mac buys nothing over just polling it directly;
 * there is no push-based file watching available from the JDK alone on
 * this platform. {@code fswatch}, installed separately
 * ({@code brew install fswatch}), wraps the real native FSEvents API and
 * delivers change events within milliseconds of the actual write.
 *
 * <p>We watch the whole containing DIRECTORY, not {@code chat.db}
 * itself - Messages runs SQLite in WAL mode, so an incoming message is
 * actually written to the sibling {@code chat.db-wal} file first (only
 * periodically checkpointed back into {@code chat.db} itself). Watching
 * the directory catches changes to any of {@code chat.db},
 * {@code chat.db-wal}, and {@code chat.db-shm}.
 */
public class ChatDbWatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatDbWatcher.class);

    private final Path watchDirectory;
    private final Runnable onChange;
    private Process fswatchProcess;
    private Thread readerThread;

    public ChatDbWatcher(Path chatDbPath, Runnable onChange) {
        this.watchDirectory = chatDbPath.getParent();
        this.onChange = onChange;
    }

    /** Returns true if the {@code fswatch} binary is available on PATH. */
    public static boolean isFswatchAvailable() {
        try {
            Process check = new ProcessBuilder("which", "fswatch")
                    .redirectErrorStream(true)
                    .start();
            return check.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Starts watching in the background. {@code -l 1} sets fswatch's own
     * internal latency to 1 second, which coalesces a rapid burst of
     * filesystem events (WAL writes can produce several in quick
     * succession for a single incoming message) into a single
     * notification, so we don't trigger a sync cycle per individual
     * low-level write.
     */
    public void start() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("fswatch", "-l", "1", watchDirectory.toString());
        pb.redirectErrorStream(true);
        fswatchProcess = pb.start();

        readerThread = new Thread(this::consumeEvents, "chatdb-fswatch-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        log.info("Watching {} for changes via fswatch (instant sync enabled)", watchDirectory);
    }

    private void consumeEvents() {
        try (var reader = new BufferedReader(
                new InputStreamReader(fswatchProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("chat.db directory change detected: {}", line);
                onChange.run();
            }
        } catch (IOException e) {
            log.warn("fswatch reader stopped unexpectedly - falling back to safety-net polling only", e);
        }
    }

    public void stop() {
        if (fswatchProcess != null) {
            fswatchProcess.destroy();
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }
}
