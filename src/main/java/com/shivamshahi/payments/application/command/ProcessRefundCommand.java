package com.shivamshahi.payments.application.command;

import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.util.Objects;

public record ProcessRefundCommand(
        PaymentId paymentId,
        RefundId refundId,
        Money amount,
        String reason
) {
    public ProcessRefundCommand {
        Objects.requireNonNull(paymentId, "PaymentId cannot be null");
        Objects.requireNonNull(refundId, "RefundId cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Refund amount must be strictly positive");
        }
    }
}
