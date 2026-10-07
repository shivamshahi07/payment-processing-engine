package com.shivamshahi.payments.infrastructure.persistence;

import com.shivamshahi.payments.application.port.RefundRepository;
import com.shivamshahi.payments.domain.model.Refund;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe concurrent in-memory refund repository.
 */
public class InMemoryRefundRepository implements RefundRepository {
    private final Map<RefundId, Refund> storage = new ConcurrentHashMap<>();
    private final Map<PaymentId, List<Refund>> byPaymentId = new ConcurrentHashMap<>();

    @Override
    public Refund save(Refund refund) {
        Objects.requireNonNull(refund, "Refund cannot be null");
        storage.put(refund.getId(), refund);
        byPaymentId.compute(refund.getPaymentId(), (k, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
            }
            if (!list.contains(refund)) {
                list.add(refund);
            }
            return list;
        });
        return refund;
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Refund> findByPaymentId(PaymentId paymentId) {
        if (paymentId == null) return Collections.emptyList();
        List<Refund> list = byPaymentId.get(paymentId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
    }
}
