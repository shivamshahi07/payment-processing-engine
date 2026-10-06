package com.shivamshahi.payments.domain.enums;

/**
 * Refund lifecycle state machine.
 */
public enum RefundStatus {
    INITIATED,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean canTransitionTo(RefundStatus nextState) {
        if (this == nextState) {
            return false;
        }
        return switch (this) {
            case INITIATED -> nextState == SUCCEEDED || nextState == FAILED;
            case SUCCEEDED, FAILED -> false;
        };
    }
}
