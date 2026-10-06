package com.financetracker.smsagent.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coalesces rapid successive {@link #trigger()} calls into a single
 * delayed run of the given action - each new call within the delay
 * window pushes the run further out, exactly like a debounced UI event
 * handler.
 *
 * <p>Used to turn "several fswatch change events for one incoming
 * message" into exactly one sync cycle, rather than one sync per raw
 * filesystem event.
 */
public class DebouncedTrigger {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "debounced-trigger"));
    private final Runnable action;
    private final Duration delay;
    private volatile ScheduledFuture<?> pending;

    public DebouncedTrigger(Runnable action, Duration delay) {
        this.action = action;
        this.delay = delay;
    }

    public synchronized void trigger() {
        if (pending != null) {
            pending.cancel(false);
        }
        pending = executor.schedule(action, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
