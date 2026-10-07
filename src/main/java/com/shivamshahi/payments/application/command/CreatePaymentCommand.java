package com.shivamshahi.payments.application.command;

import com.shivamshahi.payments.domain.model.PaymentMethod;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;

import java.util.Objects;

/**
 * Command DTO for creating a payment.
 */
public record CreatePaymentCommand(
        IdempotencyKey idempotencyKey,
        Money amount,
        PaymentMethod paymentMethod,
        String providerName
) {
    public CreatePaymentCommand {
        Objects.requireNonNull(idempotencyKey, "IdempotencyKey cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(paymentMethod, "PaymentMethod cannot be null");
        Objects.requireNonNull(providerName, "ProviderName cannot be null");
        if (providerName.isBlank()) {
            throw new IllegalArgumentException("ProviderName cannot be blank");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }

    /**
     * Checks if two commands represent equivalent requests under the same idempotency key.
     */
    public boolean isEquivalentTo(CreatePaymentCommand other) {
        if (other == null) return false;
        return this.idempotencyKey.equals(other.idempotencyKey)
                && this.amount.equals(other.amount)
                && this.paymentMethod.getType().equals(other.paymentMethod.getType())
                && this.providerName.equalsIgnoreCase(other.providerName);
    }
}
