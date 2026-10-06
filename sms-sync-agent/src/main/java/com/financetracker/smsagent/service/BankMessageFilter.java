package com.financetracker.smsagent.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides whether a message looks like a bank debit/credit alert, and
 * makes a best-effort guess at which bank sent it.
 *
 * <p>This is inherently a heuristic, not a guarantee - bank SMS formats
 * vary by bank and change over time. The design goal is HIGH PRECISION
 * (don't flood the pipeline with non-bank messages) over perfect
 * recall (it's fine to occasionally miss an unusually-worded alert; the
 * LLM downstream in parsing-service will discard chunks with no
 * transaction, so a false positive here is cheap - a false negative just
 * means one message doesn't show up, not a wrong balance).
 */
public class BankMessageFilter {

    /**
     * Common Indian bank sender-ID prefixes and names. Indian bank SMS
     * typically arrive from 6-character alphanumeric short codes like
     * "AD-HDFCBK" or "VM-SBIINB" rather than phone numbers - matching
     * against known bank name fragments anywhere in the sender ID covers
     * the vast majority of these regardless of the exact prefix scheme.
     * Extend this list for banks not covered here.
     */
    private static final List<String> KNOWN_BANK_KEYWORDS = List.of(
            "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "PNB", "BOB", "BARODA",
            "YESBANK", "IDFC", "RBL", "INDUS", "CANARA", "UNION", "FEDERAL",
            "PAYTM", "PHONEPE", "GPAY", "AMEX", "CITI", "HSBC", "STANCHART",
            "SCB", "STANDARD", "KARUR", "VIJAYA", "UCO", "DENA", "CORP",
            "IOB", "INDIAN", "CENTRAL", "ANDHRA", "BANK", "MONEY", "CASH",
            "PAY", "QR", "BILL", "UPI", "BHIM", "NEFT", "IMPS", "RTGS"
    );

    /**
     * Matches the vocabulary that shows up in almost every Indian bank
     * transaction SMS regardless of which bank sent it - "debited",
     * "credited", "spent", "withdrawn", a rupee amount, or a reference
     * to an account/available balance. Case-insensitive.
     */
    private static final Pattern TRANSACTION_LANGUAGE = Pattern.compile(
            "(?i)\\b(debited|credited|withdrawn|spent|received|txn|" +
                    "transfer|payment|purchase|purchase|card|atm|" +
                    "avl\\s?bal|available\\s?balance|a/c|acct|upi|" +
                    "deposit|refund|interest|emi|loan|installment)\\b"
    );

    /** Matches an amount like "Rs.500", "Rs 1,234.50", "INR 99.00", "₹500.00" */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(rs\\.?|inr|₹)\\s?[\\d,]+(\\.\\d{1,2})?"
    );

    /**
     * Returns true if this message should be treated as a bank
     * transaction alert and pushed into the ingestion pipeline.
     *
     * <p>Requires BOTH transaction-language AND a recognizable amount to
     * match - either alone produces too many false positives (a friend
     * texting "just spent the whole day at the beach" matches
     * transaction language alone; a message mentioning an unrelated
     * dollar figure matches amount alone).
     */
    public boolean isBankTransactionMessage(String sender, String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        boolean looksLikeBank = senderLooksLikeBank(sender) || TRANSACTION_LANGUAGE.matcher(body).find();
        boolean hasAmount = AMOUNT_PATTERN.matcher(body).find();
        return looksLikeBank && hasAmount;
    }

    /**
     * Best-effort bank-name extraction from the sender ID, falling back
     * to empty if none of the known keywords match (the caller then
     * falls back to the agent's configured default {@code bank.name}).
     */
    public Optional<String> detectBankName(String sender) {
        if (sender == null) {
            return Optional.empty();
        }
        String upperSender = sender.toUpperCase();
        return KNOWN_BANK_KEYWORDS.stream()
                .filter(upperSender::contains)
                .findFirst();
    }

    private boolean senderLooksLikeBank(String sender) {
        return detectBankName(sender).isPresent();
    }
}
