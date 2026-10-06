package com.shivamshahi.payments.domain.model;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.enums.PaymentStatus;
import com.shivamshahi.payments.domain.enums.RefundStatus;
import com.shivamshahi.payments.domain.exception.InvalidPaymentStateException;
import com.shivamshahi.payments.domain.exception.InvalidRefundException;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Payment aggregate root.
 * Enforces business invariants, monetary bounds, state transitions, and audit records.
 */
public class Payment {
    private final PaymentId id;
    private final IdempotencyKey idempotencyKey;
    private final Money amount;
    private final Money fee;
    private final PaymentMethod paymentMethod;
    private final String providerName;
    private final Instant createdAt;

    private PaymentStatus status;
    private String providerTransactionId;
    private String failureReason;
    private final List<TransactionRecord> transactionHistory;
    private final List<Refund> refunds;

    public Payment(PaymentId id,
                   IdempotencyKey idempotencyKey,
                   Money amount,
                   Money fee,
                   PaymentMethod paymentMethod,
                   String providerName) {
        Objects.requireNonNull(id, "PaymentId cannot be null");
        Objects.requireNonNull(idempotencyKey, "IdempotencyKey cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(fee, "Fee cannot be null");
        Objects.requireNonNull(paymentMethod, "PaymentMethod cannot be null");
        Objects.requireNonNull(providerName, "ProviderName cannot be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be strictly positive");
        }
        if (fee.isNegative()) {
            throw new IllegalArgumentException("Fee amount cannot be negative");
        }
        if (!amount.getCurrency().equals(fee.getCurrency())) {
            throw new IllegalArgumentException("Payment amount and fee must share the same currency");
        }

        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.fee = fee;
        this.paymentMethod = paymentMethod;
        this.providerName = providerName.trim();
        this.createdAt = Instant.now();
        this.status = PaymentStatus.CREATED;
        this.transactionHistory = new ArrayList<>();
        this.refunds = new ArrayList<>();

        recordTransaction(PaymentStatus.CREATED, PaymentStatus.CREATED, providerName, null, "Payment initialized", true);
    }

    public synchronized void markProcessing() {
        transitionTo(PaymentStatus.PROCESSING, "Payment processing initiated", null, true);
    }

    public synchronized void markSucceeded(String providerTransactionId) {
        Objects.requireNonNull(providerTransactionId, "Provider transaction ID cannot be null upon success");
        this.providerTransactionId = providerTransactionId;
        transitionTo(PaymentStatus.SUCCEEDED, "Payment succeeded through provider", providerTransactionId, true);
    }

    public synchronized void markFailed(String reason) {
        this.failureReason = reason == null ? "Unknown failure" : reason;
        transitionTo(PaymentStatus.FAILED, "Payment failed: " + this.failureReason, null, false);
    }

    public synchronized void markCancelled(String reason) {
        this.failureReason = reason == null ? "User cancelled" : reason;
        transitionTo(PaymentStatus.CANCELLED, "Payment cancelled: " + this.failureReason, null, false);
    }

    private void transitionTo(PaymentStatus newStatus, String detail, String providerTxnId, boolean success) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidPaymentStateException(
                    String.format("Invalid payment state transition from %s to %s for payment %s", this.status, newStatus, this.id.value())
            );
        }
        PaymentStatus oldStatus = this.status;
        this.status = newStatus;
        recordTransaction(oldStatus, newStatus, this.providerName, providerTxnId, detail, success);
    }

    public synchronized void recordTransaction(PaymentStatus from, PaymentStatus to, String provider, String providerTxnId, String detail, boolean success) {
        this.transactionHistory.add(TransactionRecord.of(from, to, provider, providerTxnId, detail, success));
    }

    public synchronized Refund initiateRefund(RefundId refundId, Money refundAmount, String reason) {
        if (this.status != PaymentStatus.SUCCEEDED) {
            throw new InvalidRefundException(String.format("Cannot refund payment %s in status %s. Must be SUCCEEDED.", id.value(), status));
        }
        if (!refundAmount.isPositive()) {
            throw new InvalidRefundException("Refund amount must be strictly positive");
        }
        if (!refundAmount.getCurrency().equals(this.amount.getCurrency())) {
            throw new InvalidRefundException("Refund currency must match payment currency");
        }

        Money remaining = getRemainingRefundableAmount();
        if (refundAmount.isGreaterThan(remaining)) {
            throw new InvalidRefundException(
                    String.format("Requested refund of %s exceeds remaining refundable amount %s", refundAmount, remaining)
            );
        }

        Refund refund = new Refund(refundId, this.id, refundAmount, reason);
        this.refunds.add(refund);
        recordTransaction(this.status, this.status, this.providerName, null, "Refund initiated: " + refundAmount, true);
        return refund;
    }

    public synchronized Money getCumulativeRefundedAmount() {
        Money total = Money.zero(this.amount.getCurrency());
        for (Refund r : refunds) {
            if (r.getStatus() == RefundStatus.SUCCEEDED || r.getStatus() == RefundStatus.INITIATED) {
                total = total.add(r.getAmount());
            }
        }
        return total;
    }

    public synchronized Money getRemainingRefundableAmount() {
        Money settledOrPendingRefunds = getCumulativeRefundedAmount();
        return this.amount.subtract(settledOrPendingRefunds);
    }

    public PaymentId getId() {
        return id;
    }

    public IdempotencyKey getIdempotencyKey() {
        return idempotencyKey;
    }

    public Money getAmount() {
        return amount;
    }

    public Money getFee() {
        return fee;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getProviderName() {
        return providerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized PaymentStatus getStatus() {
        return status;
    }

    public synchronized String getProviderTransactionId() {
        return providerTransactionId;
    }

    public synchronized String getFailureReason() {
        return failureReason;
    }

    public synchronized List<TransactionRecord> getTransactionHistory() {
        return Collections.unmodifiableList(new ArrayList<>(transactionHistory));
    }

    public synchronized List<Refund> getRefunds() {
        return Collections.unmodifiableList(new ArrayList<>(refunds));
    }
}
