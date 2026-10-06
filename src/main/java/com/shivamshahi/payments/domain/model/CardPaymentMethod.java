package com.shivamshahi.payments.domain.model;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;

import java.util.Objects;

public final class CardPaymentMethod implements PaymentMethod {
    private final String cardNumber; // only last 4 digits stored unmasked in production
    private final String cardHolderName;
    private final String expiryMonthYear;

    public CardPaymentMethod(String cardNumber, String cardHolderName, String expiryMonthYear) {
        Objects.requireNonNull(cardNumber, "Card number cannot be null");
        Objects.requireNonNull(cardHolderName, "Cardholder name cannot be null");
        String sanitized = cardNumber.replaceAll("\\s+", "");
        if (sanitized.length() < 13 || sanitized.length() > 19) {
            throw new IllegalArgumentException("Card number must be between 13 and 19 digits");
        }
        this.cardNumber = sanitized;
        this.cardHolderName = cardHolderName.trim();
        this.expiryMonthYear = expiryMonthYear;
    }

    @Override
    public PaymentMethodType getType() {
        return PaymentMethodType.CARD;
    }

    @Override
    public String getMaskedIdentifier() {
        int len = cardNumber.length();
        return "CARD-****" + cardNumber.substring(len - 4);
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public String getExpiryMonthYear() {
        return expiryMonthYear;
    }
}
