package com.shivamshahi.payments.domain.event;

import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;

import java.time.Instant;

public record PaymentCreatedEvent(
        PaymentId paymentId,
        IdempotencyKey idempotencyKey,
        Money amount,
        Money fee,
        String paymentMethodType,
        String providerName,
        Instant occurredAt
) implements PaymentDomainEvent {
    @Override
    public String eventType() {
        return "PAYMENT_CREATED";
    }
}
