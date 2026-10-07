package com.shivamshahi.payments.infrastructure.retry;

import java.time.Duration;

public class ExponentialBackoffPolicy implements BackoffPolicy {
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;

    public ExponentialBackoffPolicy(Duration initialDelay, double multiplier, Duration maxDelay) {
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay;
    }

    public static ExponentialBackoffPolicy defaultPolicy() {
        return new ExponentialBackoffPolicy(Duration.ofMillis(100), 2.0, Duration.ofSeconds(2));
    }

    public static ExponentialBackoffPolicy zeroDelayPolicy() {
        return new ExponentialBackoffPolicy(Duration.ZERO, 1.0, Duration.ZERO);
    }

    @Override
    public Duration getDelayForAttempt(int attemptNumber) {
        if (attemptNumber <= 1 || initialDelay.isZero()) {
            return initialDelay;
        }
        long millis = (long) (initialDelay.toMillis() * Math.pow(multiplier, attemptNumber - 1));
        if (millis > maxDelay.toMillis()) {
            return maxDelay;
        }
        return Duration.ofMillis(millis);
    }
}
