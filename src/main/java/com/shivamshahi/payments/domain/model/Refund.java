package com.shivamshahi.payments.domain.model;

import com.shivamshahi.payments.domain.enums.RefundStatus;
import com.shivamshahi.payments.domain.exception.InvalidPaymentStateException;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.time.Instant;
import java.util.Objects;

/**
 * Refund aggregate entity.
 */
public class Refund {
    private final RefundId id;
    private final PaymentId paymentId;
    private final Money amount;
    private final String reason;
    private final Instant createdAt;
    private RefundStatus status;
    private String providerRefundId;
    private String failureReason;

    public Refund(RefundId id, PaymentId paymentId, Money amount, String reason) {
        Objects.requireNonNull(id, "RefundId cannot be null");
        Objects.requireNonNull(paymentId, "PaymentId cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Refund amount must be strictly positive");
        }
        this.id = id;
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason == null ? "" : reason.trim();
        this.createdAt = Instant.now();
        this.status = RefundStatus.INITIATED;
    }

    public synchronized void markSucceeded(String providerRefundId) {
        if (!status.canTransitionTo(RefundStatus.SUCCEEDED)) {
            throw new InvalidPaymentStateException(String.format("Cannot transition refund %s from %s to SUCCEEDED", id.value(), status));
        }
        this.status = RefundStatus.SUCCEEDED;
        this.providerRefundId = providerRefundId;
    }

    public synchronized void markFailed(String reason) {
        if (!status.canTransitionTo(RefundStatus.FAILED)) {
            throw new InvalidPaymentStateException(String.format("Cannot transition refund %s from %s to FAILED", id.value(), status));
        }
        this.status = RefundStatus.FAILED;
        this.failureReason = reason;
    }

    public RefundId getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public Money getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized RefundStatus getStatus() {
        return status;
    }

    public synchronized String getProviderRefundId() {
        return providerRefundId;
    }

    public synchronized String getFailureReason() {
        return failureReason;
    }
}
