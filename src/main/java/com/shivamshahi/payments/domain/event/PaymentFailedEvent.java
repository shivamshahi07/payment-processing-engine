package com.shivamshahi.payments.domain.event;

import com.shivamshahi.payments.domain.valueobject.PaymentId;

import java.time.Instant;

public record PaymentFailedEvent(
        PaymentId paymentId,
        String providerName,
        String failureReason,
        Instant occurredAt
) implements PaymentDomainEvent {
    @Override
    public String eventType() {
        return "PAYMENT_FAILED";
    }
}
