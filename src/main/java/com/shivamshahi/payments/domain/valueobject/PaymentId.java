package com.shivamshahi.payments.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed unique identifier for a Payment.
 */
public record PaymentId(String value) {
    public PaymentId {
        Objects.requireNonNull(value, "PaymentId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PaymentId value cannot be blank");
        }
    }

    public static PaymentId generate() {
        return new PaymentId("pay_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public static PaymentId of(String value) {
        return new PaymentId(value);
    }
}
