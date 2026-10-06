package com.shivamshahi.payments.domain.exception;

public class InvalidPaymentStateException extends PaymentDomainException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
