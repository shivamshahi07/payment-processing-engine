package com.shivamshahi.payments.infrastructure.fee;

import com.shivamshahi.payments.application.port.FeeCalculationStrategy;
import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * UPI fee strategy: Zero fee up to threshold, or small flat fee (e.g. 0.00% or low fixed cap).
 */
public class UpiFeeCalculationStrategy implements FeeCalculationStrategy {
    private final BigDecimal percentageRate;

    public UpiFeeCalculationStrategy(BigDecimal percentageRate) {
        this.percentageRate = Objects.requireNonNull(percentageRate, "Percentage rate cannot be null");
    }

    public UpiFeeCalculationStrategy() {
        this(BigDecimal.ZERO); // Typical UPI peer-to-merchant zero/negligible fee
    }

    @Override
    public PaymentMethodType getSupportedMethodType() {
        return PaymentMethodType.UPI;
    }

    @Override
    public Money calculateFee(Money amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return amount.multiply(percentageRate);
    }
}
