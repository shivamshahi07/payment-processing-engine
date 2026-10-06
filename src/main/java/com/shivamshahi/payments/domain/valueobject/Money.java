package com.shivamshahi.payments.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary value object adhering to safe monetary arithmetic.
 * Uses BigDecimal with 2 decimal places (or currency minor units) and explicit RoundingMode.HALF_UP.
 */
public final class Money implements Comparable<Money> {
    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        this.amount = amount.setScale(currency.getDefaultFractionDigits() >= 0 ? currency.getDefaultFractionDigits() : 2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(String amountStr, String currencyCode) {
        Objects.requireNonNull(amountStr, "Amount string cannot be null");
        Objects.requireNonNull(currencyCode, "Currency code cannot be null");
        return new Money(new BigDecimal(amountStr.trim()), Currency.getInstance(currencyCode.trim().toUpperCase()));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "Multiplication factor cannot be null");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    private void validateSameCurrency(Money other) {
        Objects.requireNonNull(other, "Money comparison target cannot be null");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(String.format("Currency mismatch: cannot operate on %s and %s",
                    this.currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
    }

    @Override
    public int compareTo(Money o) {
        validateSameCurrency(o);
        return this.amount.compareTo(o.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return String.format("%s %s", currency.getCurrencyCode(), amount.toPlainString());
    }
}
