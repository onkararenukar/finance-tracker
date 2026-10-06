package com.financetracker.smsagent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankMessageFilterTest {

    private final BankMessageFilter filter = new BankMessageFilter();

    @Test
    void isBankTransactionMessage_typicalDebitAlert_returnsTrue() {
        boolean result = filter.isBankTransactionMessage(
                "AD-HDFCBK",
                "Rs.500.00 debited from A/c XX1234 on 01-Aug-26. Avl Bal Rs.12,345.67 -HDFC Bank");
        assertThat(result).isTrue();
    }

    @Test
    void isBankTransactionMessage_creditAlertFromUnknownSender_stillDetectedViaLanguageAndAmount() {
        boolean result = filter.isBankTransactionMessage(
                "+919876543210",
                "INR 3000.00 credited to your account via UPI. Ref no 123456789012");
        assertThat(result).isTrue();
    }

    @Test
    void isBankTransactionMessage_ordinaryConversation_returnsFalse() {
        boolean result = filter.isBankTransactionMessage(
                "+919876543210",
                "Hey, are we still on for dinner tonight?");
        assertThat(result).isFalse();
    }

    @Test
    void isBankTransactionMessage_mentionsMoneyButNoBankLanguage_returnsFalse() {
        // Regression guard for the "friend mentions a dollar figure" false-positive case.
        boolean result = filter.isBankTransactionMessage(
                "+919876543210",
                "That jacket was Rs.2000, such a steal!");
        assertThat(result).isFalse();
    }

    @Test
    void detectBankName_recognizesKnownBankFromSenderId() {
        assertThat(filter.detectBankName("AD-HDFCBK")).contains("HDFC");
        assertThat(filter.detectBankName("VM-SBIINB")).contains("SBI");
    }

    @Test
    void detectBankName_unknownSender_returnsEmpty() {
        assertThat(filter.detectBankName("+919876543210")).isEmpty();
    }
}
