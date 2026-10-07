package com.shivamshahi.payments.infrastructure.provider;

import com.shivamshahi.payments.application.port.PaymentProvider;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SecureGateway simulated legacy provider.
 * Simulates strict validation, rate limiting, and explicit retryable error behaviors.
 */
public class SecureGatewayProviderAdapter implements PaymentProvider {
    private final AtomicInteger attemptCount = new AtomicInteger(0);
    private int failNextAttempts = 0;
    private boolean simulateInsufficientFunds = false;

    public void setFailNextAttempts(int count) {
        this.failNextAttempts = count;
        this.attemptCount.set(0);
    }

    public void setSimulateInsufficientFunds(boolean simulateInsufficientFunds) {
        this.simulateInsufficientFunds = simulateInsufficientFunds;
    }

    @Override
    public String getName() {
        return "SECUREGATEWAY";
    }

    @Override
    public boolean supports(Payment payment) {
        return true;
    }

    @Override
    public ProviderTransactionResponse processPayment(Payment payment) {
        int currentAttempt = attemptCount.incrementAndGet();

        if (simulateInsufficientFunds) {
            return ProviderTransactionResponse.failure("SG_INSUFFICIENT_FUNDS", "Account has insufficient balance", false);
        }

        if (currentAttempt <= failNextAttempts) {
            return ProviderTransactionResponse.failure("SG_GATEWAY_BUSY", "Bank network busy, please retry", true);
        }

        return ProviderTransactionResponse.success("sg_txn_" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public ProviderRefundResponse processRefund(Payment payment, Refund refund) {
        return ProviderRefundResponse.success("sg_ref_" + UUID.randomUUID().toString().substring(0, 8));
    }
}
