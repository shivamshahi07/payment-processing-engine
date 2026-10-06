package com.shivamshahi.payments.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed unique identifier for a Refund.
 */
public record RefundId(String value) {
    public RefundId {
        Objects.requireNonNull(value, "RefundId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RefundId value cannot be blank");
        }
    }

    public static RefundId generate() {
        return new RefundId("ref_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public static RefundId of(String value) {
        return new RefundId(value);
    }
}
