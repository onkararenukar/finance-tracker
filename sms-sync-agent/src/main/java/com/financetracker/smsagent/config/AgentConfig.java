package com.financetracker.smsagent.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads agent configuration from {@code ~/.finance-tracker/agent.properties}
 * (created with sensible defaults on first run if it doesn't exist), with
 * every value overridable via a matching environment variable.
 *
 * <p>Deliberately a plain properties file rather than YAML/Spring config -
 * this agent has exactly five settings and runs as a single user-owned
 * process; a full configuration framework would be more ceremony than
 * the problem calls for.
 *
 * @param chatDbPath          path to Messages' chat.db (default: the
 *                            standard macOS location under your home
 *                            directory)
 * @param ingestionServiceUrl base URL of ingestion-service's REST API
 * @param syncIntervalSeconds SAFETY-NET poll interval - runs a sync
 *                            cycle on this fixed schedule regardless of
 *                            file-watch events, in case fswatch ever
 *                            misses one. When fswatch is unavailable,
 *                            this also becomes the PRIMARY sync
 *                            mechanism (see {@code SmsSyncAgentApplication}),
 *                            so a shorter default is used than when it's
 *                            purely a safety net.
 * @param debounceMillis      how long to wait after the last detected
 *                            chat.db change before actually running a
 *                            sync cycle, coalescing a burst of rapid
 *                            filesystem events (one incoming message can
 *                            produce several WAL writes) into one cycle
 * @param stateFilePath       where the last-processed-message high-water
 *                            mark is persisted between runs
 * @param bankName            the bank name recorded on every uploaded
 *                            transaction batch (kept as a single config
 *                            value for v1 - see BankMessageFilter for the
 *                            path to per-message bank detection instead)
 */
public record AgentConfig(
        Path chatDbPath,
        String ingestionServiceUrl,
        long syncIntervalSeconds,
        long debounceMillis,
        Path stateFilePath,
        String bankName
) {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".finance-tracker");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("agent.properties");

    public static AgentConfig load() throws IOException {
        ensureConfigFileExists();

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
        }

        return new AgentConfig(
                Path.of(resolve(props, "chat.db.path",
                        System.getProperty("user.home") + "/Library/Messages/chat.db")),
                resolve(props, "ingestion.service.url", "http://localhost:8081"),
                Long.parseLong(resolve(props, "sync.interval.seconds", "120")),
                Long.parseLong(resolve(props, "debounce.millis", "3000")),
                Path.of(resolve(props, "state.file.path", CONFIG_DIR.resolve("sms-agent-state.json").toString())),
                resolve(props, "bank.name", "Unknown Bank")
        );
    }

    /** Environment variable (if set) always wins over the properties file, which wins over the hardcoded default. */
    private static String resolve(Properties props, String key, String defaultValue) {
        String envKey = "FINANCE_TRACKER_" + key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return props.getProperty(key, defaultValue);
    }

    private static void ensureConfigFileExists() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            return;
        }
        Files.createDirectories(CONFIG_DIR);
        String template = """
                # Finance Tracker - SMS Sync Agent configuration
                # Every key here can also be set via an environment variable, e.g.
                # FINANCE_TRACKER_INGESTION_SERVICE_URL=http://localhost:8081

                chat.db.path=%s/Library/Messages/chat.db
                ingestion.service.url=http://localhost:8081

                # Safety-net poll interval (seconds). With fswatch installed, syncs are
                # triggered instantly on new messages and this just guards against a
                # missed event. WITHOUT fswatch, this becomes the only sync mechanism -
                # consider lowering it (e.g. to 15-30) if you don't install fswatch.
                sync.interval.seconds=120

                # How long (ms) to wait after the last detected chat.db change before
                # actually syncing, to coalesce a burst of rapid writes into one cycle.
                debounce.millis=3000

                state.file.path=%s/sms-agent-state.json
                bank.name=Unknown Bank
                """.formatted(System.getProperty("user.home"), CONFIG_DIR);
        Files.writeString(CONFIG_FILE, template);
    }
}
