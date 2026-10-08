package com.shivamshahi.payments.application;

import com.shivamshahi.payments.application.command.CreatePaymentCommand;
import com.shivamshahi.payments.application.command.ProcessRefundCommand;
import com.shivamshahi.payments.application.service.FeeCalculationService;
import com.shivamshahi.payments.application.service.PaymentProcessingService;
import com.shivamshahi.payments.domain.enums.PaymentStatus;
import com.shivamshahi.payments.domain.enums.RefundStatus;
import com.shivamshahi.payments.domain.event.PaymentCreatedEvent;
import com.shivamshahi.payments.domain.event.PaymentDomainEvent;
import com.shivamshahi.payments.domain.event.PaymentSucceededEvent;
import com.shivamshahi.payments.domain.exception.IdempotencyConflictException;
import com.shivamshahi.payments.domain.model.CardPaymentMethod;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;
import com.shivamshahi.payments.domain.model.UpiPaymentMethod;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.RefundId;
import com.shivamshahi.payments.infrastructure.event.InMemoryEventPublisher;
import com.shivamshahi.payments.infrastructure.fee.CardFeeCalculationStrategy;
import com.shivamshahi.payments.infrastructure.fee.UpiFeeCalculationStrategy;
import com.shivamshahi.payments.infrastructure.persistence.InMemoryPaymentRepository;
import com.shivamshahi.payments.infrastructure.persistence.InMemoryRefundRepository;
import com.shivamshahi.payments.infrastructure.provider.FastPayProviderAdapter;
import com.shivamshahi.payments.infrastructure.provider.PaymentProviderRegistry;
import com.shivamshahi.payments.infrastructure.provider.SecureGatewayProviderAdapter;
import com.shivamshahi.payments.infrastructure.retry.ConfigurableRetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProcessingServiceTest {
    private PaymentProcessingService service;
    private FastPayProviderAdapter fastPay;
    private SecureGatewayProviderAdapter secureGateway;
    private List<PaymentDomainEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        InMemoryPaymentRepository paymentRepo = new InMemoryPaymentRepository();
        InMemoryRefundRepository refundRepo = new InMemoryRefundRepository();
        PaymentProviderRegistry registry = new PaymentProviderRegistry();

        fastPay = new FastPayProviderAdapter();
        secureGateway = new SecureGatewayProviderAdapter();
        registry.registerProvider(fastPay);
        registry.registerProvider(secureGateway);

        FeeCalculationService feeService = new FeeCalculationService();
        feeService.registerStrategy(new CardFeeCalculationStrategy());
        feeService.registerStrategy(new UpiFeeCalculationStrategy());

        ConfigurableRetryPolicy retryPolicy = ConfigurableRetryPolicy.ofFastForTests(3);
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();
        publishedEvents = new ArrayList<>();
        eventPublisher.subscribe(publishedEvents::add);

        service = new PaymentProcessingService(paymentRepo, refundRepo, registry, feeService, retryPolicy, eventPublisher);
    }

    @Test
    @DisplayName("Should process payment successfully and publish domain events")
    void shouldProcessPaymentSuccessfully() {
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("test-idem-1"),
                Money.of("100.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                "FASTPAY"
        );

        Payment payment = service.processPayment(cmd);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isNotNull();
        assertThat(publishedEvents).hasSize(2);
        assertThat(publishedEvents.get(0)).isInstanceOf(PaymentCreatedEvent.class);
        assertThat(publishedEvents.get(1)).isInstanceOf(PaymentSucceededEvent.class);
    }

    @Test
    @DisplayName("Should retry retryable errors up to max attempts and succeed")
    void shouldRetryAndSucceed() {
        secureGateway.setFailNextAttempts(2);

        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("test-idem-retry"),
                Money.of("50.00", "USD"),
                new UpiPaymentMethod("test@upi"),
                "SECUREGATEWAY"
        );

        Payment payment = service.processPayment(cmd);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getTransactionHistory()).hasSize(5); // Initial + Attempt 1 + Attempt 2 + Attempt 3 + Succeeded
    }

    @Test
    @DisplayName("Should detect idempotency conflict for mismatched payload")
    void shouldThrowOnIdempotencyConflict() {
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("shared-key"),
                Money.of("100.00", "USD"),
                new UpiPaymentMethod("alice@upi"),
                "FASTPAY"
        );
        service.processPayment(cmd);

        CreatePaymentCommand conflictingCmd = new CreatePaymentCommand(
                IdempotencyKey.of("shared-key"),
                Money.of("200.00", "USD"), // Different amount
                new UpiPaymentMethod("alice@upi"),
                "FASTPAY"
        );

        assertThatThrownBy(() -> service.processPayment(conflictingCmd))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Should maintain idempotency under multithreaded concurrent requests")
    void shouldHandleConcurrentIdempotentRequests() throws InterruptedException {
        IdempotencyKey key = IdempotencyKey.of("concurrent-race-key");
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                key,
                Money.of("120.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John", "12/28"),
                "FASTPAY"
        );

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Payment[] results = new Payment[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results[index] = service.processPayment(cmd);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Payment first = results[0];
        assertThat(first).isNotNull();
        for (Payment p : results) {
            assertThat(p.getId()).isEqualTo(first.getId());
        }
    }
}
