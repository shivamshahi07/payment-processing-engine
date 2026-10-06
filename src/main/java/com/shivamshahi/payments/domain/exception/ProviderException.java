package com.shivamshahi.payments.domain.exception;

public class ProviderException extends PaymentDomainException {
    private final boolean retryable;
    private final String providerErrorCode;

    public ProviderException(String message, boolean retryable, String providerErrorCode) {
        super(message);
        this.retryable = retryable;
        this.providerErrorCode = providerErrorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getProviderErrorCode() {
        return providerErrorCode;
    }
}
