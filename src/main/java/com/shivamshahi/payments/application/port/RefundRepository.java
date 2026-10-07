package com.shivamshahi.payments.application.port;

import com.shivamshahi.payments.domain.model.Refund;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;

import java.util.List;
import java.util.Optional;

public interface RefundRepository {
    Refund save(Refund refund);
    Optional<Refund> findById(RefundId id);
    List<Refund> findByPaymentId(PaymentId paymentId);
}
