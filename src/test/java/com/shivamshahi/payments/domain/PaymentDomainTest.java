package com.shivamshahi.payments.domain;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.enums.PaymentStatus;
import com.shivamshahi.payments.domain.exception.InvalidPaymentStateException;
import com.shivamshahi.payments.domain.exception.InvalidRefundException;
import com.shivamshahi.payments.domain.model.CardPaymentMethod;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;
import com.shivamshahi.payments.domain.model.UpiPaymentMethod;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDomainTest {
    private final Currency usd = Currency.getInstance("USD");

    @Test
    @DisplayName("Should initialize payment in CREATED status with valid history")
    void shouldInitializePaymentCorrectly() {
        Payment payment = new Payment(
                PaymentId.generate(),
                IdempotencyKey.of("key-1"),
                Money.of("100.00", "USD"),
                Money.of("2.50", "USD"),
                new CardPaymentMethod("4111222233334444", "John", "12/28"),
                "FASTPAY"
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getAmount()).isEqualTo(Money.of("100.00", "USD"));
        assertThat(payment.getTransactionHistory()).hasSize(1);
        assertThat(payment.getTransactionHistory().get(0).detail()).isEqualTo("Payment initialized");
    }

    @Test
    @DisplayName("Should reject negative or zero payment amount")
    void shouldRejectInvalidAmount() {
        assertThatThrownBy(() -> new Payment(
                PaymentId.generate(),
                IdempotencyKey.of("key-2"),
                Money.of("0.00", "USD"),
                Money.of("0.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John", "12/28"),
                "FASTPAY"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly positive");
    }

    @Test
    @DisplayName("Should follow state machine transitions correctly")
    void shouldFollowLifecycleTransitions() {
        Payment payment = new Payment(
                PaymentId.generate(),
                IdempotencyKey.of("key-3"),
                Money.of("100.00", "USD"),
                Money.of("0.00", "USD"),
                new UpiPaymentMethod("test@upi"),
                "FASTPAY"
        );

        payment.markProcessing();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        payment.markSucceeded("txn_123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("txn_123");

        // Attempt invalid transition from terminal state
        assertThatThrownBy(payment::markProcessing)
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    @DisplayName("Should correctly track partial refunds and cumulative balance")
    void shouldTrackRefundBalance() {
        Payment payment = new Payment(
                PaymentId.generate(),
                IdempotencyKey.of("key-4"),
                Money.of("100.00", "USD"),
                Money.of("0.00", "USD"),
                new UpiPaymentMethod("test@upi"),
                "FASTPAY"
        );
        payment.markProcessing();
        payment.markSucceeded("txn_123");

        Refund r1 = payment.initiateRefund(RefundId.generate(), Money.of("40.00", "USD"), "First refund");
        assertThat(payment.getRemainingRefundableAmount()).isEqualTo(Money.of("60.00", "USD"));

        Refund r2 = payment.initiateRefund(RefundId.generate(), Money.of("60.00", "USD"), "Second refund");
        assertThat(payment.getRemainingRefundableAmount()).isEqualTo(Money.of("0.00", "USD"));

        // Third refund must exceed remaining refundable amount
        assertThatThrownBy(() -> payment.initiateRefund(RefundId.generate(), Money.of("0.01", "USD"), "Exceeding refund"))
                .isInstanceOf(InvalidRefundException.class)
                .hasMessageContaining("exceeds remaining refundable amount");
    }
}
