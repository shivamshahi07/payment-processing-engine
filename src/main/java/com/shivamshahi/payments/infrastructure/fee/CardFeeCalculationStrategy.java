package com.shivamshahi.payments.infrastructure.fee;

import com.shivamshahi.payments.application.port.FeeCalculationStrategy;
import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Card fee strategy: Flat fee + percentage (e.g. 2.0% + $0.30 or min fee).
 */
public class CardFeeCalculationStrategy implements FeeCalculationStrategy {
    private final BigDecimal percentageRate; // e.g. 0.020 for 2.0%
    private final BigDecimal flatFeeAmount;   // e.g. 0.30

    public CardFeeCalculationStrategy(BigDecimal percentageRate, BigDecimal flatFeeAmount) {
        this.percentageRate = Objects.requireNonNull(percentageRate, "Percentage rate cannot be null");
        this.flatFeeAmount = Objects.requireNonNull(flatFeeAmount, "Flat fee cannot be null");
    }

    public CardFeeCalculationStrategy() {
        this(new BigDecimal("0.020"), new BigDecimal("0.30")); // Default 2.0% + 0.30
    }

    @Override
    public PaymentMethodType getSupportedMethodType() {
        return PaymentMethodType.CARD;
    }

    @Override
    public Money calculateFee(Money amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Money percentFee = amount.multiply(percentageRate);
        Money flatFee = Money.of(flatFeeAmount, amount.getCurrency());
        return percentFee.add(flatFee);
    }
}
