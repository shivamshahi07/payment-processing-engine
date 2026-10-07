package com.shivamshahi.payments.application.port;

import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;

public interface PaymentProvider {
    String getName();
    boolean supports(Payment payment);
    ProviderTransactionResponse processPayment(Payment payment);
    ProviderRefundResponse processRefund(Payment payment, Refund refund);

    record ProviderTransactionResponse(
            boolean success,
            String providerTransactionId,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        public static ProviderTransactionResponse success(String providerTransactionId) {
            return new ProviderTransactionResponse(true, providerTransactionId, null, null, false);
        }

        public static ProviderTransactionResponse failure(String errorCode, String errorMessage, boolean retryable) {
            return new ProviderTransactionResponse(false, null, errorCode, errorMessage, retryable);
        }
    }

    record ProviderRefundResponse(
            boolean success,
            String providerRefundId,
            String errorCode,
            String errorMessage
    ) {
        public static ProviderRefundResponse success(String providerRefundId) {
            return new ProviderRefundResponse(true, providerRefundId, null, null);
        }

        public static ProviderRefundResponse failure(String errorCode, String errorMessage) {
            return new ProviderRefundResponse(false, null, errorCode, errorMessage);
        }
    }
}
