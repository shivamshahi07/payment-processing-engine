package com.shivamshahi.payments.infrastructure.retry;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;

    static Sleeper systemSleeper() {
        return duration -> {
            if (!duration.isZero() && !duration.isNegative()) {
                Thread.sleep(duration.toMillis());
            }
        };
    }

    static Sleeper noOpSleeper() {
        return duration -> {};
    }
}
