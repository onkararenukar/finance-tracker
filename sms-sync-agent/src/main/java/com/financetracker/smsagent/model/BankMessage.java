package com.financetracker.smsagent.model;

import java.time.Instant;

/**
 * One row read from the Messages app's {@code chat.db}, after we've
 * decided (via {@link com.financetracker.smsagent.service.BankMessageFilter})
 * that it looks like a bank transaction alert.
 *
 * @param rowId    the message table's ROWID in chat.db - used as our
 *                 high-water mark so we never re-process the same
 *                 message twice across agent runs (see {@link
 *                 com.financetracker.smsagent.service.SyncStateStore})
 * @param sentAt   when the message was received, converted from Apple's
 *                 Core Data epoch (nanoseconds since 2001-01-01 UTC)
 * @param sender   the sender's handle - a phone number, short code
 *                 (e.g. "AD-HDFCBK"), or contact identifier
 * @param body     the raw message text
 */
public record BankMessage(long rowId, Instant sentAt, String sender, String body) {
}
