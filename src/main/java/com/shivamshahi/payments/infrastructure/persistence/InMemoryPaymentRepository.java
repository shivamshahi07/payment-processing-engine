package com.shivamshahi.payments.infrastructure.persistence;

import com.shivamshahi.payments.application.port.PaymentRepository;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.PaymentId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe concurrent in-memory payment repository.
 */
public class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<PaymentId, Payment> storageById = new ConcurrentHashMap<>();
    private final Map<IdempotencyKey, PaymentId> indexByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        Objects.requireNonNull(payment, "Payment cannot be null");
        storageById.put(payment.getId(), payment);
        indexByIdempotencyKey.put(payment.getIdempotencyKey(), payment.getId());
        return payment;
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storageById.get(id));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        PaymentId paymentId = indexByIdempotencyKey.get(idempotencyKey);
        if (paymentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storageById.get(paymentId));
    }
}
