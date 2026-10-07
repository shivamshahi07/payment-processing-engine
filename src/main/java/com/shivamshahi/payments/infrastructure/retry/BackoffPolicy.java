package com.shivamshahi.payments.infrastructure.retry;

import java.time.Duration;

public interface BackoffPolicy {
    Duration getDelayForAttempt(int attemptNumber);
}
