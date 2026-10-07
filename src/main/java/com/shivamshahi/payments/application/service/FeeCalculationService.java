package com.shivamshahi.payments.application.service;

import com.shivamshahi.payments.application.port.FeeCalculationStrategy;
import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.valueobject.Money;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and service for fee strategies.
 */
public class FeeCalculationService {
    private final Map<PaymentMethodType, FeeCalculationStrategy> strategies = new ConcurrentHashMap<>();

    public void registerStrategy(FeeCalculationStrategy strategy) {
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        strategies.put(strategy.getSupportedMethodType(), strategy);
    }

    public Money calculateFee(PaymentMethodType methodType, Money amount) {
        FeeCalculationStrategy strategy = strategies.get(methodType);
        if (strategy == null) {
            throw new IllegalArgumentException("No fee calculation strategy registered for " + methodType);
        }
        return strategy.calculateFee(amount);
    }
}
