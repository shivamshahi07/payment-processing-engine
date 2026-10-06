package com.shivamshahi.payments.domain.exception;

public class InvalidRefundException extends PaymentDomainException {
    public InvalidRefundException(String message) {
        super(message);
    }
}
