package com.shivamshahi.payments.infrastructure;

import com.shivamshahi.payments.application.port.PaymentProvider;
import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.model.CardPaymentMethod;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.domain.valueobject.RefundId;
import com.shivamshahi.payments.infrastructure.fee.CardFeeCalculationStrategy;
import com.shivamshahi.payments.infrastructure.fee.UpiFeeCalculationStrategy;
import com.shivamshahi.payments.infrastructure.provider.FastPayProviderAdapter;
import com.shivamshahi.payments.infrastructure.provider.PaymentProviderRegistry;
import com.shivamshahi.payments.infrastructure.provider.SecureGatewayProviderAdapter;
import com.shivamshahi.payments.infrastructure.retry.ConfigurableRetryPolicy;
import com.shivamshahi.payments.infrastructure.retry.ExponentialBackoffPolicy;
import com.shivamshahi.payments.infrastructure.retry.Sleeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfrastructureComponentsTest {

    @Test
    @DisplayName("Test Fee calculation strategies")
    void testFeeStrategies() {
        CardFeeCalculationStrategy cardFee = new CardFeeCalculationStrategy(new BigDecimal("0.025"), new BigDecimal("0.50"));
        assertThat(cardFee.getSupportedMethodType()).isEqualTo(PaymentMethodType.CARD);
        Money fee1 = cardFee.calculateFee(Money.of("100.00", "USD"));
        assertThat(fee1.getAmount()).isEqualTo(new BigDecimal("3.00")); // 100 * 0.025 + 0.50

        UpiFeeCalculationStrategy upiFee = new UpiFeeCalculationStrategy(new BigDecimal("0.005"));
        assertThat(upiFee.getSupportedMethodType()).isEqualTo(PaymentMethodType.UPI);
        Money fee2 = upiFee.calculateFee(Money.of("200.00", "USD"));
        assertThat(fee2.getAmount()).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Test Retry and backoff policies")
    void testRetryPolicy() throws InterruptedException {
        ExponentialBackoffPolicy backoff = new ExponentialBackoffPolicy(Duration.ofMillis(50), 2.0, Duration.ofMillis(500));
        assertThat(backoff.getDelayForAttempt(1)).isEqualTo(Duration.ofMillis(50));
        assertThat(backoff.getDelayForAttempt(2)).isEqualTo(Duration.ofMillis(100));
        assertThat(backoff.getDelayForAttempt(3)).isEqualTo(Duration.ofMillis(200));

        ConfigurableRetryPolicy retryPolicy = new ConfigurableRetryPolicy(3, backoff, Sleeper.noOpSleeper());
        assertThat(retryPolicy.shouldRetry(1, true)).isTrue();
        assertThat(retryPolicy.shouldRetry(1, false)).isFalse();
        assertThat(retryPolicy.shouldRetry(3, true)).isFalse();
        retryPolicy.backoff(1);
    }

    @Test
    @DisplayName("Test Provider Registry and Adapters")
    void testProviderRegistry() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry();
        FastPayProviderAdapter fastPay = new FastPayProviderAdapter();
        SecureGatewayProviderAdapter sg = new SecureGatewayProviderAdapter();

        registry.registerProvider(fastPay);
        registry.registerProvider(sg);

        assertThat(registry.getProvider("FASTPAY")).isPresent();
        assertThat(registry.getProvider("SECUREGATEWAY")).isPresent();
        assertThat(registry.getProvider("UNKNOWN")).isEmpty();

        assertThatThrownBy(() -> registry.getRequiredProvider("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
