package com.financetracker.smsagent;

import com.financetracker.smsagent.config.AgentConfig;
import com.financetracker.smsagent.service.BankMessageFilter;
import com.financetracker.smsagent.service.ChatDbWatcher;
import com.financetracker.smsagent.service.DebouncedTrigger;
import com.financetracker.smsagent.service.IngestionUploader;
import com.financetracker.smsagent.service.MessagesDbReader;
import com.financetracker.smsagent.service.SmsSyncScheduler;
import com.financetracker.smsagent.service.SyncStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Run with {@code java -jar sms-sync-agent.jar} directly on
 * your Mac (NOT in Docker - see this module's pom.xml javadoc for why).
 *
 * <h2>How syncing is triggered</h2>
 * <ol>
 *   <li><b>Primary (instant):</b> if {@code fswatch} is installed
 *       ({@code brew install fswatch}), {@link ChatDbWatcher} reacts to
 *       real FSEvents-backed filesystem notifications the moment
 *       Messages writes a new row to chat.db - typically within a
 *       second of the text actually arriving on your phone/Mac.</li>
 *   <li><b>Safety net (always on):</b> a fixed-interval poll
 *       ({@code sync.interval.seconds}, default 120s) runs regardless,
 *       in case an fswatch event is ever missed (e.g. the process was
 *       briefly busy).</li>
 *   <li><b>Fallback (if fswatch isn't installed):</b> the same
 *       fixed-interval poll becomes the ONLY mechanism - we log a
 *       one-time suggestion to install fswatch for instant syncing, and
 *       recommend shortening the interval in that case.</li>
 * </ol>
 *
 * <p>Before running for the first time:
 * <ol>
 *   <li>Grant Full Disk Access to whatever app/terminal you'll launch
 *       this from (System Settings -> Privacy &amp; Security -> Full
 *       Disk Access), then fully quit and reopen that app.</li>
 *   <li>{@code brew install fswatch} for instant sync (optional but
 *       recommended - otherwise you're limited to poll-interval latency).</li>
 *   <li>Make sure ingestion-service is reachable (default:
 *       {@code http://localhost:8081}).</li>
 * </ol>
 */
public class SmsSyncAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(SmsSyncAgentApplication.class);

    public static void main(String[] args) throws Exception {
        AgentConfig config = AgentConfig.load();

        log.info("Starting SMS sync agent");
        log.info("  chat.db path         : {}", config.chatDbPath());
        log.info("  ingestion-service     : {}", config.ingestionServiceUrl());
        log.info("  safety-net interval   : {}s", config.syncIntervalSeconds());
        log.info("  debounce              : {}ms", config.debounceMillis());
        log.info("  default bank name     : {}", config.bankName());

        var bankMessageFilter = new BankMessageFilter();
        var scheduler = new SmsSyncScheduler(
                new MessagesDbReader(config.chatDbPath()),
                bankMessageFilter,
                new IngestionUploader(config.ingestionServiceUrl(), config.bankName(), bankMessageFilter),
                new SyncStateStore(config.stateFilePath())
        );

        // The safety-net poll always runs, regardless of whether fswatch
        // is available - see class javadoc for the three-tier strategy.
        ScheduledExecutorService safetyNetExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "sms-sync-safety-net"));
        safetyNetExecutor.scheduleAtFixedRate(
                scheduler::runOnce, 0, config.syncIntervalSeconds(), TimeUnit.SECONDS);

        ChatDbWatcher watcher = null;
        if (ChatDbWatcher.isFswatchAvailable()) {
            var debouncedSync = new DebouncedTrigger(scheduler::runOnce, Duration.ofMillis(config.debounceMillis()));
            watcher = new ChatDbWatcher(config.chatDbPath(), debouncedSync::trigger);
            watcher.start();
            log.info("Instant sync ENABLED via fswatch - new bank messages will be processed within seconds");
        } else {
            log.warn("""
                    fswatch not found on PATH - falling back to poll-only mode (interval={}s).
                    For near-instant processing of new messages, install it with:
                        brew install fswatch
                    and restart this agent.""", config.syncIntervalSeconds());
        }

        ChatDbWatcher finalWatcher = watcher;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down SMS sync agent...");
            safetyNetExecutor.shutdown();
            if (finalWatcher != null) {
                finalWatcher.stop();
            }
        }));
    }
}
