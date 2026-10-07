package com.shivamshahi.payments.domain.event;

import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;

import java.time.Instant;

public record PaymentSucceededEvent(
        PaymentId paymentId,
        Money amount,
        String providerName,
        String providerTransactionId,
        Instant occurredAt
) implements PaymentDomainEvent {
    @Override
    public String eventType() {
        return "PAYMENT_SUCCEEDED";
    }
}
