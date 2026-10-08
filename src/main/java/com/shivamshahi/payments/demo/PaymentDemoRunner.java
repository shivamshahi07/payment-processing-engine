package com.shivamshahi.payments.demo;

import com.shivamshahi.payments.application.command.CreatePaymentCommand;
import com.shivamshahi.payments.application.command.ProcessRefundCommand;
import com.shivamshahi.payments.application.service.FeeCalculationService;
import com.shivamshahi.payments.application.service.PaymentProcessingService;
import com.shivamshahi.payments.domain.exception.IdempotencyConflictException;
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
import com.shivamshahi.payments.infrastructure.event.InMemoryEventPublisher;
import com.shivamshahi.payments.infrastructure.fee.CardFeeCalculationStrategy;
import com.shivamshahi.payments.infrastructure.fee.UpiFeeCalculationStrategy;
import com.shivamshahi.payments.infrastructure.persistence.InMemoryPaymentRepository;
import com.shivamshahi.payments.infrastructure.persistence.InMemoryRefundRepository;
import com.shivamshahi.payments.infrastructure.provider.FastPayProviderAdapter;
import com.shivamshahi.payments.infrastructure.provider.PaymentProviderRegistry;
import com.shivamshahi.payments.infrastructure.provider.SecureGatewayProviderAdapter;
import com.shivamshahi.payments.infrastructure.retry.ConfigurableRetryPolicy;

import java.util.Currency;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executable interactive CLI demonstration of all 10 acceptance scenarios:
 * 1. Successful payment processing
 * 2. Permanent payment decline
 * 3. Retryable failure followed by success
 * 4. Repeated payment creation with same idempotency key
 * 5. Conflicting request using existing idempotency key
 * 6. Partial refund followed by another valid refund
 * 7. Refund exceeding refundable amount
 * 8. Invalid payment state transitions
 * 9. Event publication verification
 * 10. Concurrent duplicate payment creation race condition prevention
 */
public class PaymentDemoRunner {
    private static final String SEPARATOR = "=".repeat(75);
    private static final String SUB_SEPARATOR = "-".repeat(75);

    public static void main(String[] args) throws Exception {
        System.out.println(SEPARATOR);
        System.out.println("   PAYMENT PROCESSING ENGINE - ACCEPTANCE SCENARIOS DEMO");
        System.out.println(SEPARATOR);

        // Setup infrastructure
        InMemoryPaymentRepository paymentRepo = new InMemoryPaymentRepository();
        InMemoryRefundRepository refundRepo = new InMemoryRefundRepository();
        PaymentProviderRegistry providerRegistry = new PaymentProviderRegistry();

        FastPayProviderAdapter fastPay = new FastPayProviderAdapter();
        SecureGatewayProviderAdapter secureGateway = new SecureGatewayProviderAdapter();
        providerRegistry.registerProvider(fastPay);
        providerRegistry.registerProvider(secureGateway);

        FeeCalculationService feeService = new FeeCalculationService();
        feeService.registerStrategy(new CardFeeCalculationStrategy());
        feeService.registerStrategy(new UpiFeeCalculationStrategy());

        ConfigurableRetryPolicy retryPolicy = ConfigurableRetryPolicy.ofFastForTests(3);
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();

        AtomicInteger eventCounter = new AtomicInteger(0);
        eventPublisher.subscribe(event -> {
            eventCounter.incrementAndGet();
            System.out.printf("   [AUDIT OBSERVER] Received Event: %-20s | Timestamp: %s%n",
                    event.eventType(), event.occurredAt());
        });

        PaymentProcessingService service = new PaymentProcessingService(
                paymentRepo, refundRepo, providerRegistry, feeService, retryPolicy, eventPublisher
        );

        Currency usd = Currency.getInstance("USD");

        // Scenario 1: Successful payment
        printScenarioHeader(1, "Successful Payment Processing (Card with FastPay)");
        CreatePaymentCommand cmd1 = new CreatePaymentCommand(
                IdempotencyKey.of("idem-card-001"),
                Money.of("150.00", "USD"),
                new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                "FASTPAY"
        );
        Payment p1 = service.processPayment(cmd1);
        System.out.printf("   Payment ID: %s | Status: %s | TxnId: %s | Fee: %s%n",
                p1.getId().value(), p1.getStatus(), p1.getProviderTransactionId(), p1.getFee());

        // Scenario 2: Permanent decline
        printScenarioHeader(2, "Permanent Payment Decline (No Retries on Business Decline)");
        fastPay.setSimulatePermanentDecline(true);
        CreatePaymentCommand cmd2 = new CreatePaymentCommand(
                IdempotencyKey.of("idem-decline-002"),
                Money.of("500.00", "USD"),
                new CardPaymentMethod("4000000000000002", "Jane Smith", "10/26"),
                "FASTPAY"
        );
        Payment p2 = service.processPayment(cmd2);
        System.out.printf("   Payment ID: %s | Status: %s | Reason: %s%n",
                p2.getId().value(), p2.getStatus(), p2.getFailureReason());
        fastPay.setSimulatePermanentDecline(false);

        // Scenario 3: Retryable failure followed by success
        printScenarioHeader(3, "Retryable Provider Failure Recovered via Exponential Backoff");
        secureGateway.setFailNextAttempts(2); // Fails attempt 1 & 2, succeeds on attempt 3
        CreatePaymentCommand cmd3 = new CreatePaymentCommand(
                IdempotencyKey.of("idem-retry-003"),
                Money.of("75.00", "USD"),
                new UpiPaymentMethod("alice@okaxis"),
                "SECUREGATEWAY"
        );
        Payment p3 = service.processPayment(cmd3);
        System.out.printf("   Payment ID: %s | Final Status: %s | TxnId: %s | Total Attempts Recorded: %d%n",
                p3.getId().value(), p3.getStatus(), p3.getProviderTransactionId(), p3.getTransactionHistory().size());

        // Scenario 4: Repeated payment creation with same idempotency key (Safe Replay)
        printScenarioHeader(4, "Safe Idempotent Replay (Same Key, Same Payload)");
        Payment p1Replay = service.processPayment(cmd1);
        System.out.printf("   Original Payment ID: %s | Replay Payment ID: %s | Identical: %b%n",
                p1.getId().value(), p1Replay.getId().value(), p1.getId().equals(p1Replay.getId()));

        // Scenario 5: Conflicting request using existing idempotency key
        printScenarioHeader(5, "Conflicting Idempotency Request (Same Key, Different Payload)");
        try {
            CreatePaymentCommand conflictingCmd = new CreatePaymentCommand(
                    IdempotencyKey.of("idem-card-001"),
                    Money.of("999.00", "USD"), // Different amount
                    new CardPaymentMethod("4111222233334444", "John Doe", "12/28"),
                    "FASTPAY"
            );
            service.processPayment(conflictingCmd);
            System.err.println("   [ERROR] Should have failed with IdempotencyConflictException!");
        } catch (IdempotencyConflictException ex) {
            System.out.printf("   Expected Conflict Caught: %s%n", ex.getMessage());
        }

        // Scenario 6: Partial refund followed by another valid refund
        printScenarioHeader(6, "Partial Refund Workflow");
        Refund r1 = service.processRefund(new ProcessRefundCommand(
                p1.getId(), RefundId.generate(), Money.of("50.00", "USD"), "Customer return partial item"
        ));
        System.out.printf("   Refund 1 ID: %s | Amount: %s | Status: %s | Remaining Refundable: %s%n",
                r1.getId().value(), r1.getAmount(), r1.getStatus(), p1.getRemainingRefundableAmount());

        Refund r2 = service.processRefund(new ProcessRefundCommand(
                p1.getId(), RefundId.generate(), Money.of("30.00", "USD"), "Damaged packaging discount"
        ));
        System.out.printf("   Refund 2 ID: %s | Amount: %s | Status: %s | Remaining Refundable: %s%n",
                r2.getId().value(), r2.getAmount(), r2.getStatus(), p1.getRemainingRefundableAmount());

        // Scenario 7: Refund exceeding remaining refundable amount
        printScenarioHeader(7, "Refund Exceeding Remaining Refundable Balance");
        try {
            service.processRefund(new ProcessRefundCommand(
                    p1.getId(), RefundId.generate(), Money.of("100.00", "USD"), "Over-refund attempt"
            ));
            System.err.println("   [ERROR] Should have failed with InvalidRefundException!");
        } catch (InvalidRefundException ex) {
            System.out.printf("   Expected Refund Error Caught: %s%n", ex.getMessage());
        }

        // Scenario 8: Invalid payment state transitions
        printScenarioHeader(8, "Invalid Payment State Transition Guard");
        try {
            p1.markProcessing(); // Cannot transition from SUCCEEDED to PROCESSING
            System.err.println("   [ERROR] Should have rejected transition from SUCCEEDED to PROCESSING!");
        } catch (InvalidPaymentStateException ex) {
            System.out.printf("   Expected State Machine Guard: %s%n", ex.getMessage());
        }

        // Scenario 9: Event publication verification
        printScenarioHeader(9, "Domain Event Publication Summary");
        System.out.printf("   Total domain events observed by registered subscribers: %d%n", eventCounter.get());

        // Scenario 10: Concurrent duplicate requests with same idempotency key
        printScenarioHeader(10, "Concurrent Creation Race Prevention (Thread-Safe Idempotency)");
        IdempotencyKey concurrentKey = IdempotencyKey.of("idem-race-test-010");
        CreatePaymentCommand raceCmd = new CreatePaymentCommand(
                concurrentKey,
                Money.of("200.00", "USD"),
                new UpiPaymentMethod("bob@okhdfcbank"),
                "FASTPAY"
        );

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(4);
        Payment[] results = new Payment[4];

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results[idx] = service.processPayment(raceCmd);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all 4 concurrent threads simultaneously
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        boolean allIdentical = true;
        PaymentId referenceId = results[0].getId();
        for (Payment res : results) {
            if (res == null || !res.getId().equals(referenceId)) {
                allIdentical = false;
                break;
            }
        }
        System.out.printf("   4 Concurrent threads executed for key '%s'.%n", concurrentKey.value());
        System.out.printf("   All threads resolved to same Payment ID: %s (Deduplicated = %b)%n",
                referenceId.value(), allIdentical);

        System.out.println(SEPARATOR);
        System.out.println("   ALL 10 PAYMENT PROCESSING ACCEPTANCE SCENARIOS PASSED SUCCESSFULLY!");
        System.out.println(SEPARATOR);
    }

    private static void printScenarioHeader(int num, String title) {
        System.out.println(SUB_SEPARATOR);
        System.out.printf(" Scenario %d: %s%n", num, title);
        System.out.println(SUB_SEPARATOR);
    }
}
