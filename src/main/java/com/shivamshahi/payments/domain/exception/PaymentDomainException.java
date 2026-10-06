package com.shivamshahi.payments.domain.exception;

public class PaymentDomainException extends RuntimeException {
    public PaymentDomainException(String message) {
        super(message);
    }

    public PaymentDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
