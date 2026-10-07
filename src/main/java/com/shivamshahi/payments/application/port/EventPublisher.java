package com.shivamshahi.payments.application.port;

import com.shivamshahi.payments.domain.event.PaymentDomainEvent;

public interface EventPublisher {
    void publish(PaymentDomainEvent event);
}
