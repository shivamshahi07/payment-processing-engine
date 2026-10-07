package com.shivamshahi.payments.application.port;

import com.shivamshahi.payments.domain.event.PaymentDomainEvent;

@FunctionalInterface
public interface EventSubscriber {
    void onEvent(PaymentDomainEvent event);
}
