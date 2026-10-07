package com.shivamshahi.payments.infrastructure.retry;

import com.shivamshahi.payments.application.port.RetryPolicy;

import java.time.Duration;
import java.util.Objects;

public class ConfigurableRetryPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final BackoffPolicy backoffPolicy;
    private final Sleeper sleeper;

    public ConfigurableRetryPolicy(int maxAttempts, BackoffPolicy backoffPolicy, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Max attempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.backoffPolicy = Objects.requireNonNull(backoffPolicy, "Backoff policy cannot be null");
        this.sleeper = Objects.requireNonNull(sleeper, "Sleeper cannot be null");
    }

    public static ConfigurableRetryPolicy ofDefaults() {
        return new ConfigurableRetryPolicy(3, ExponentialBackoffPolicy.defaultPolicy(), Sleeper.systemSleeper());
    }

    public static ConfigurableRetryPolicy ofFastForTests(int maxAttempts) {
        return new ConfigurableRetryPolicy(maxAttempts, ExponentialBackoffPolicy.zeroDelayPolicy(), Sleeper.noOpSleeper());
    }

    @Override
    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Override
    public boolean shouldRetry(int currentAttempt, boolean isRetryableError) {
        return isRetryableError && currentAttempt < maxAttempts;
    }

    @Override
    public void backoff(int currentAttempt) throws InterruptedException {
        Duration delay = backoffPolicy.getDelayForAttempt(currentAttempt);
        sleeper.sleep(delay);
    }
}
