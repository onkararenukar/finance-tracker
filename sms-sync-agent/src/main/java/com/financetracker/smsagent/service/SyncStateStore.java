package com.financetracker.smsagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the last-processed {@code chat.db} ROWID to a small local
 * JSON file, so the agent has a durable high-water mark across restarts
 * (without this, every restart would re-scan the entire message history
 * and re-upload everything since we don't dedupe by message content -
 * only by this checkpoint).
 */
public class SyncStateStore {

    private final Path stateFilePath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SyncStateStore(Path stateFilePath) {
        this.stateFilePath = stateFilePath;
    }

    public long loadLastProcessedRowId() {
        if (!Files.exists(stateFilePath)) {
            return 0L; // no prior state - start from the beginning of chat.db history
        }
        try {
            State state = objectMapper.readValue(stateFilePath.toFile(), State.class);
            return state.lastProcessedRowId();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sync state file: " + stateFilePath, e);
        }
    }

    public void saveLastProcessedRowId(long rowId) {
        try {
            Files.createDirectories(stateFilePath.getParent());
            objectMapper.writeValue(stateFilePath.toFile(), new State(rowId));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write sync state file: " + stateFilePath, e);
        }
    }

    private record State(long lastProcessedRowId) {
    }
}
