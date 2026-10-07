package com.shivamshahi.payments.infrastructure.provider;

import com.shivamshahi.payments.application.port.PaymentProvider;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FastPay simulated provider.
 * High-speed gateway simulating low failure rate, but configurable for deterministic testing.
 */
public class FastPayProviderAdapter implements PaymentProvider {
    private final AtomicInteger attemptCount = new AtomicInteger(0);
    private int failNextAttempts = 0;
    private boolean simulatePermanentDecline = false;
    private boolean simulateRefundFailure = false;

    public void setFailNextAttempts(int count) {
        this.failNextAttempts = count;
        this.attemptCount.set(0);
    }

    public void setSimulatePermanentDecline(boolean simulatePermanentDecline) {
        this.simulatePermanentDecline = simulatePermanentDecline;
    }

    public void setSimulateRefundFailure(boolean simulateRefundFailure) {
        this.simulateRefundFailure = simulateRefundFailure;
    }

    @Override
    public String getName() {
        return "FASTPAY";
    }

    @Override
    public boolean supports(Payment payment) {
        return true;
    }

    @Override
    public ProviderTransactionResponse processPayment(Payment payment) {
        int currentAttempt = attemptCount.incrementAndGet();

        if (simulatePermanentDecline) {
            return ProviderTransactionResponse.failure("FASTPAY_CARD_DECLINED", "Card declined by issuing bank", false);
        }

        if (currentAttempt <= failNextAttempts) {
            return ProviderTransactionResponse.failure("FASTPAY_TIMEOUT", "FastPay connection timeout", true);
        }

        return ProviderTransactionResponse.success("fp_txn_" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public ProviderRefundResponse processRefund(Payment payment, Refund refund) {
        if (simulateRefundFailure) {
            return ProviderRefundResponse.failure("FASTPAY_REFUND_REJECTED", "Issuer rejected refund credit");
        }
        return ProviderRefundResponse.success("fp_ref_" + UUID.randomUUID().toString().substring(0, 8));
    }
}
