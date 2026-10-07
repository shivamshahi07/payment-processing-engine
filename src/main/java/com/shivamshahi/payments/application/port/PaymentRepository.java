package com.shivamshahi.payments.application.port;

import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.PaymentId;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId id);
    Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}
