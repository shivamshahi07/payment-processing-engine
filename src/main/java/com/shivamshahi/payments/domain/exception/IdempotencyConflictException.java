package com.shivamshahi.payments.domain.exception;

public class IdempotencyConflictException extends PaymentDomainException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
