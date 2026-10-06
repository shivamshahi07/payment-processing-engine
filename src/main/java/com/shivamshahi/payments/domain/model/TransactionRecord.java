package com.shivamshahi.payments.domain.model;

import com.shivamshahi.payments.domain.enums.PaymentStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable audit record of state changes, provider calls, and transitions.
 */
public record TransactionRecord(
        Instant timestamp,
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        String providerName,
        String providerTransactionId,
        String detail,
        boolean success
) {
    public TransactionRecord {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        Objects.requireNonNull(detail, "Detail cannot be null");
    }

    public static TransactionRecord of(PaymentStatus from, PaymentStatus to, String provider, String providerTxnId, String detail, boolean success) {
        return new TransactionRecord(Instant.now(), from, to, provider, providerTxnId, detail, success);
    }
}
