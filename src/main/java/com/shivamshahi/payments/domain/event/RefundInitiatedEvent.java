package com.shivamshahi.payments.domain.event;

import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.time.Instant;

public record RefundInitiatedEvent(
        RefundId refundId,
        PaymentId paymentId,
        Money amount,
        String reason,
        Instant occurredAt
) implements PaymentDomainEvent {
    @Override
    public String eventType() {
        return "REFUND_INITIATED";
    }
}
