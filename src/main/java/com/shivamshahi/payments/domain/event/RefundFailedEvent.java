package com.shivamshahi.payments.domain.event;

import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.time.Instant;

public record RefundFailedEvent(
        RefundId refundId,
        PaymentId paymentId,
        String failureReason,
        Instant occurredAt
) implements PaymentDomainEvent {
    @Override
    public String eventType() {
        return "REFUND_FAILED";
    }
}
