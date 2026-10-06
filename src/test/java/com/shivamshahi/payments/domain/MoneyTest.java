package com.shivamshahi.payments.domain;

import com.shivamshahi.payments.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {
    private final Currency usd = Currency.getInstance("USD");
    private final Currency inr = Currency.getInstance("INR");

    @Test
    @DisplayName("Should perform exact monetary additions and subtractions without precision loss")
    void testPrecisionArithmetic() {
        Money m1 = Money.of("10.50", "USD");
        Money m2 = Money.of("20.25", "USD");

        Money sum = m1.add(m2);
        assertThat(sum.getAmount()).isEqualTo(new BigDecimal("30.75"));

        Money diff = sum.subtract(m1);
        assertThat(diff).isEqualTo(m2);
    }

    @Test
    @DisplayName("Should reject arithmetic across different currencies")
    void testCurrencyMismatch() {
        Money m1 = Money.of("10.00", "USD");
        Money m2 = Money.of("10.00", "INR");

        assertThatThrownBy(() -> m1.add(m2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }
}
