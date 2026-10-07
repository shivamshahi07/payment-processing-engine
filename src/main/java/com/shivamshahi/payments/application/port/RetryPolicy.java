package com.shivamshahi.payments.application.port;

public interface RetryPolicy {
    int getMaxAttempts();
    boolean shouldRetry(int currentAttempt, boolean isRetryableError);
    void backoff(int currentAttempt) throws InterruptedException;
}
