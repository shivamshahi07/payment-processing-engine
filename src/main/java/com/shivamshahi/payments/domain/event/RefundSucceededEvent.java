package com.shivamshahi.payments.domain.event;

import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.time.Instant;

public record RefundSucceededEvent(
        RefundId refundId,
        PaymentId paymentId,
        Money amount,
        String providerRefundId,
        Instant occurredAt
) implements PaymentDomainEvent {
    @Override
    public String eventType() {
        return "REFUND_SUCCEEDED";
    }
}
