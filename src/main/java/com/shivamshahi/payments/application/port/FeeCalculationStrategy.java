package com.shivamshahi.payments.application.port;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.valueobject.Money;

public interface FeeCalculationStrategy {
    PaymentMethodType getSupportedMethodType();
    Money calculateFee(Money amount);
}
