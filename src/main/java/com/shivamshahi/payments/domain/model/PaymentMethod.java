package com.shivamshahi.payments.domain.model;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;

import java.util.Objects;

/**
 * Domain representation of payment instrument / method details.
 */
public sealed interface PaymentMethod permits CardPaymentMethod, UpiPaymentMethod {
    PaymentMethodType getType();
    String getMaskedIdentifier();
}
