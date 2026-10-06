package com.shivamshahi.payments.domain.enums;

import java.util.Set;

/**
 * Payment lifecycle state machine.
 * Valid transitions:
 * CREATED -> PROCESSING
 * CREATED -> CANCELLED
 * PROCESSING -> SUCCEEDED
 * PROCESSING -> FAILED
 */
public enum PaymentStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean canTransitionTo(PaymentStatus nextState) {
        if (this == nextState) {
            return false;
        }
        return switch (this) {
            case CREATED -> nextState == PROCESSING || nextState == CANCELLED;
            case PROCESSING -> nextState == SUCCEEDED || nextState == FAILED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }
}
