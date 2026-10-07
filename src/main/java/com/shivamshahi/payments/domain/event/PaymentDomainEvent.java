package com.shivamshahi.payments.domain.event;

import java.time.Instant;

/**
 * Marker interface for all Payment domain events.
 */
public interface PaymentDomainEvent {
    Instant occurredAt();
    String eventType();
}
