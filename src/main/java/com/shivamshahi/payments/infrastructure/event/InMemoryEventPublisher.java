package com.shivamshahi.payments.infrastructure.event;

import com.shivamshahi.payments.application.port.EventPublisher;
import com.shivamshahi.payments.application.port.EventSubscriber;
import com.shivamshahi.payments.domain.event.PaymentDomainEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe in-memory domain event publisher.
 * Ensures subscriber exceptions are caught and logged so one rogue subscriber does not break orchestration.
 */
public class InMemoryEventPublisher implements EventPublisher {
    private static final Logger LOGGER = Logger.getLogger(InMemoryEventPublisher.class.getName());
    private final List<EventSubscriber> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(EventSubscriber subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "Subscriber cannot be null"));
    }

    public void unsubscribe(EventSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    @Override
    public void publish(PaymentDomainEvent event) {
        Objects.requireNonNull(event, "Domain event cannot be null");
        LOGGER.info(() -> String.format("[EVENT] %s published: %s", event.eventType(), event));
        for (EventSubscriber subscriber : subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error notifying subscriber for event " + event.eventType(), ex);
            }
        }
    }
}
