package com.shivamshahi.payments.application;

import com.shivamshahi.payments.application.command.CreatePaymentCommand;
import com.shivamshahi.payments.application.command.ProcessRefundCommand;
import com.shivamshahi.payments.application.service.FeeCalculationService;
import com.shivamshahi.payments.application.service.PaymentProcessingService;
import com.shivamshahi.payments.domain.enums.PaymentMethodType;
import com.shivamshahi.payments.domain.enums.PaymentStatus;
import com.shivamshahi.payments.domain.enums.RefundStatus;
import com.shivamshahi.payments.domain.exception.InvalidRefundException;
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

import java.util.Currency;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRefundAndEdgeTest {
    private PaymentProcessingService service;
    private FastPayProviderAdapter fastPay;
    private SecureGatewayProviderAdapter secureGateway;
    private InMemoryRefundRepository refundRepo;

    @BeforeEach
    void setUp() {
        InMemoryPaymentRepository paymentRepo = new InMemoryPaymentRepository();
        refundRepo = new InMemoryRefundRepository();
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

        service = new PaymentProcessingService(paymentRepo, refundRepo, registry, feeService, retryPolicy, eventPublisher);
    }

    @Test
    @DisplayName("Should successfully execute full refund")
    void testFullRefund() {
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("idem-refund-full"),
                Money.of("80.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                "FASTPAY"
        );
        Payment payment = service.processPayment(cmd);

        Refund refund = service.processRefund(new ProcessRefundCommand(
                payment.getId(),
                RefundId.generate(),
                Money.of("80.00", "USD"),
                "Customer cancellation"
        ));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(payment.getRemainingRefundableAmount()).isEqualTo(Money.of("0.00", "USD"));
        assertThat(refundRepo.findByPaymentId(payment.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Should handle simulated provider refund rejection")
    void testRefundProviderFailure() {
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("idem-refund-fail"),
                Money.of("50.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                "FASTPAY"
        );
        Payment payment = service.processPayment(cmd);

        fastPay.setSimulateRefundFailure(true);
        assertThatThrownBy(() -> service.processRefund(new ProcessRefundCommand(
                payment.getId(),
                RefundId.generate(),
                Money.of("20.00", "USD"),
                "Test fail"
        ))).isInstanceOf(InvalidRefundException.class)
          .hasMessageContaining("Provider refund rejected");

        fastPay.setSimulateRefundFailure(false);
    }

    @Test
    @DisplayName("Should permanently fail payment when provider declines without retrying")
    void testPermanentDeclineDoesNotRetry() {
        fastPay.setSimulatePermanentDecline(true);
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("idem-declined"),
                Money.of("100.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                "FASTPAY"
        );

        Payment payment = service.processPayment(cmd);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).contains("Card declined");
        assertThat(payment.getTransactionHistory()).hasSize(4); // Initial + Processing + Attempt 1 failed + Payment Failed

        fastPay.setSimulatePermanentDecline(false);
    }

    @Test
    @DisplayName("Should prevent concurrent refunds exceeding the total balance")
    void testConcurrentRefundRace() throws InterruptedException {
        CreatePaymentCommand cmd = new CreatePaymentCommand(
                IdempotencyKey.of("idem-concurrent-refund"),
                Money.of("100.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                "FASTPAY"
        );
        Payment payment = service.processPayment(cmd);

        int attempts = 3;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.processRefund(new ProcessRefundCommand(
                            payment.getId(),
                            RefundId.generate(),
                            Money.of("60.00", "USD"),
                            "Simultaneous refund"
                    ));
                    successCount.incrementAndGet();
                } catch (InvalidRefundException expected) {
                    failureCount.incrementAndGet();
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

        // Exactly 1 refund of 60.00 can succeed out of 100.00; the second & third request of 60.00 must fail
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(2);
        assertThat(payment.getRemainingRefundableAmount()).isEqualTo(Money.of("40.00", "USD"));
    }
}
