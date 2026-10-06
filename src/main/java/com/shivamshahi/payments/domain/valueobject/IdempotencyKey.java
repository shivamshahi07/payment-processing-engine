package com.shivamshahi.payments.domain.valueobject;

import java.util.Objects;

/**
 * Idempotency key ensuring deterministic request processing and deduplication.
 */
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "IdempotencyKey value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey value cannot be blank");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value.trim());
    }
}
