package com.shivamshahi.payments.domain.model;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;

import java.util.Objects;
import java.util.regex.Pattern;

public final class UpiPaymentMethod implements PaymentMethod {
    private static final Pattern VPA_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$");
    private final String vpa; // e.g. shivam@okhdfcbank

    public UpiPaymentMethod(String vpa) {
        Objects.requireNonNull(vpa, "UPI VPA cannot be null");
        String trimmed = vpa.trim();
        if (!VPA_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid UPI VPA format: " + vpa);
        }
        this.vpa = trimmed;
    }

    @Override
    public PaymentMethodType getType() {
        return PaymentMethodType.UPI;
    }

    @Override
    public String getMaskedIdentifier() {
        int atIndex = vpa.indexOf('@');
        if (atIndex <= 2) {
            return "UPI-***" + vpa.substring(atIndex);
        }
        return "UPI-" + vpa.charAt(0) + "***" + vpa.substring(atIndex - 1);
    }

    public String getVpa() {
        return vpa;
    }
}
