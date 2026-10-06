package com.financetracker.smsagent.service;

import com.financetracker.smsagent.model.BankMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * One full sync cycle: read new messages since the last checkpoint,
 * filter down to bank transaction alerts, upload them, and - only on
 * full success - advance the checkpoint.
 */
public class SmsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SmsSyncScheduler.class);

    private final MessagesDbReader messagesDbReader;
    private final BankMessageFilter bankMessageFilter;
    private final IngestionUploader ingestionUploader;
    private final SyncStateStore syncStateStore;

    public SmsSyncScheduler(MessagesDbReader messagesDbReader, BankMessageFilter bankMessageFilter,
                             IngestionUploader ingestionUploader, SyncStateStore syncStateStore) {
        this.messagesDbReader = messagesDbReader;
        this.bankMessageFilter = bankMessageFilter;
        this.ingestionUploader = ingestionUploader;
        this.syncStateStore = syncStateStore;
    }

    /**
     * Runs one sync cycle. Deliberately catches and logs every exception
     * rather than letting one fail cycle propagate - this method is
     * called on a fixed interval for the lifetime of the agent process,
     * and a single bad cycle (e.g. a transient network blip) should
     * never take the whole agent down.
     */
    public void runOnce() {
        try {
            long lastRowId = syncStateStore.loadLastProcessedRowId();
            List<BankMessage> newMessages = messagesDbReader.readNewMessages(lastRowId);

            if (newMessages.isEmpty()) {
                log.debug("No new messages since rowId={}", lastRowId);
                return;
            }

            List<BankMessage> bankMessages = newMessages.stream()
                    .filter(m -> bankMessageFilter.isBankTransactionMessage(m.sender(), m.body()))
                    .toList();

            log.info("Found {} bank transaction message(s) out of {} new message(s)",
                    bankMessages.size(), newMessages.size());

            boolean uploaded = ingestionUploader.uploadBatch(bankMessages);

            // Advance the checkpoint past ALL new messages (not just the
            // bank ones) as long as the upload succeeded - non-bank
            // messages are intentionally skipped forever, not retried,
            // since they'll never match the filter differently next time.
            if (uploaded) {
                long newHighWaterMark = newMessages.get(newMessages.size() - 1).rowId();
                syncStateStore.saveLastProcessedRowId(newHighWaterMark);
                log.info("Checkpoint advanced to rowId={}", newHighWaterMark);
            } else {
                log.warn("Upload did not fully succeed - checkpoint NOT advanced, will retry next cycle");
            }

        } catch (Exception e) {
            log.error("Sync cycle failed - will retry next cycle", e);
        }
    }
}
