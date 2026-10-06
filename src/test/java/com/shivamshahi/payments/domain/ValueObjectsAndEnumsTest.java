package com.shivamshahi.payments.domain;

import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.enums.PaymentStatus;
import com.shivamshahi.payments.domain.enums.RefundStatus;
import com.shivamshahi.payments.domain.exception.InvalidPaymentStateException;
import com.shivamshahi.payments.domain.exception.PaymentDomainException;
import com.shivamshahi.payments.domain.exception.ProviderException;
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

class ValueObjectsAndEnumsTest {
    private final Currency usd = Currency.getInstance("USD");

    @Test
    @DisplayName("Test Money edge cases and comparisons")
    void testMoneyEdgeCases() {
        Money m1 = Money.of(new BigDecimal("15.50"), usd);
        Money m2 = Money.of("15.50", "USD");
        Money m3 = Money.of("20.00", "USD");
        Money zero = Money.zero(usd);

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        assertThat(m1.isPositive()).isTrue();
        assertThat(m1.isNegative()).isFalse();
        assertThat(m3.isGreaterThan(m1)).isTrue();
        assertThat(m1.isLessThan(m3)).isTrue();
        assertThat(m1.compareTo(m2)).isEqualTo(0);
        assertThat(m1.toString()).isEqualTo("USD 15.50");
        assertThat(zero.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test ID value objects validation")
    void testIds() {
        PaymentId pid = PaymentId.of("pay_test_123");
        assertThat(pid.value()).isEqualTo("pay_test_123");
        assertThatThrownBy(() -> PaymentId.of(" "))
                .isInstanceOf(IllegalArgumentException.class);

        RefundId rid = RefundId.of("ref_test_123");
        assertThat(rid.value()).isEqualTo("ref_test_123");
        assertThatThrownBy(() -> RefundId.of(""))
                .isInstanceOf(IllegalArgumentException.class);

        IdempotencyKey ikey = IdempotencyKey.of("  key-1  ");
        assertThat(ikey.value()).isEqualTo("key-1");
        assertThatThrownBy(() -> IdempotencyKey.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Test PaymentMethod models masking and validation")
    void testPaymentMethods() {
        CardPaymentMethod card = new CardPaymentMethod("4111 2222 3333 4444", "John Doe", "12/28");
        assertThat(card.getType()).isEqualTo(PaymentMethodType.CARD);
        assertThat(card.getMaskedIdentifier()).isEqualTo("CARD-****4444");
        assertThat(card.getCardHolderName()).isEqualTo("John Doe");
        assertThat(card.getExpiryMonthYear()).isEqualTo("12/28");

        assertThatThrownBy(() -> new CardPaymentMethod("123", "John", "12/28"))
                .isInstanceOf(IllegalArgumentException.class);

        UpiPaymentMethod upi = new UpiPaymentMethod("shivam@bank");
        assertThat(upi.getType()).isEqualTo(PaymentMethodType.UPI);
        assertThat(upi.getMaskedIdentifier()).contains("UPI-");
        assertThat(upi.getVpa()).isEqualTo("shivam@bank");

        UpiPaymentMethod shortUpi = new UpiPaymentMethod("ab@okaxis");
        assertThat(shortUpi.getMaskedIdentifier()).isEqualTo("UPI-***@okaxis");

        assertThatThrownBy(() -> new UpiPaymentMethod("invalid-vpa"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Test Payment and Refund state transitions and terminal states")
    void testStateTransitions() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(PaymentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();

        assertThat(RefundStatus.INITIATED.canTransitionTo(RefundStatus.SUCCEEDED)).isTrue();
        assertThat(RefundStatus.INITIATED.canTransitionTo(RefundStatus.FAILED)).isTrue();
        assertThat(RefundStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(RefundStatus.FAILED.isTerminal()).isTrue();

        Refund refund = new Refund(RefundId.generate(), PaymentId.generate(), Money.of("10.00", "USD"), "Reason");
        refund.markFailed("Failed response");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureReason()).isEqualTo("Failed response");

        assertThatThrownBy(() -> refund.markSucceeded("txn_1"))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    @DisplayName("Test Payment cancellation and validation")
    void testPaymentCancellationAndValidation() {
        Payment payment = new Payment(
                PaymentId.generate(),
                IdempotencyKey.of("key-cancel"),
                Money.of("50.00", "USD"),
                Money.of("1.00", "USD"),
                new UpiPaymentMethod("test@upi"),
                "FASTPAY"
        );
        payment.markCancelled("User closed window");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getFailureReason()).isEqualTo("User closed window");

        // Provider Exception test
        ProviderException pex = new ProviderException("Network error", true, "ERR_NET");
        assertThat(pex.isRetryable()).isTrue();
        assertThat(pex.getProviderErrorCode()).isEqualTo("ERR_NET");

        PaymentDomainException baseEx = new PaymentDomainException("Base message", pex);
        assertThat(baseEx.getCause()).isEqualTo(pex);
    }
}
