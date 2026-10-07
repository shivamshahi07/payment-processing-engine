package com.shivamshahi.payments.application.service;

import com.shivamshahi.payments.application.command.CreatePaymentCommand;
import com.shivamshahi.payments.application.command.ProcessRefundCommand;
import com.shivamshahi.payments.application.port.EventPublisher;
import com.shivamshahi.payments.application.port.PaymentProvider;
import com.shivamshahi.payments.application.port.PaymentRepository;
import com.shivamshahi.payments.application.port.RefundRepository;
import com.shivamshahi.payments.application.port.RetryPolicy;
import com.shivamshahi.payments.domain.enums.PaymentStatus;
import com.shivamshahi.payments.domain.event.PaymentCreatedEvent;
import com.shivamshahi.payments.domain.event.PaymentFailedEvent;
import com.shivamshahi.payments.domain.event.PaymentSucceededEvent;
import com.shivamshahi.payments.domain.event.RefundFailedEvent;
import com.shivamshahi.payments.domain.event.RefundInitiatedEvent;
import com.shivamshahi.payments.domain.event.RefundSucceededEvent;
import com.shivamshahi.payments.domain.exception.IdempotencyConflictException;
import com.shivamshahi.payments.domain.exception.InvalidRefundException;
import com.shivamshahi.payments.domain.model.Payment;
import com.shivamshahi.payments.domain.model.Refund;
import com.shivamshahi.payments.domain.valueobject.IdempotencyKey;
import com.shivamshahi.payments.domain.valueobject.Money;
import com.shivamshahi.payments.domain.valueobject.PaymentId;
import com.shivamshahi.payments.infrastructure.provider.PaymentProviderRegistry;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrator service coordinating payment and refund workflows.
 * Implements thread-safe idempotency locks, fee calculation, provider retries, and domain event publishing.
 */
public class PaymentProcessingService {
    private static final Logger LOGGER = Logger.getLogger(PaymentProcessingService.class.getName());

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final FeeCalculationService feeCalculationService;
    private final RetryPolicy retryPolicy;
    private final EventPublisher eventPublisher;

    // Concurrency control: Fine-grained locks per idempotency key to prevent race conditions during creation
    private final Map<IdempotencyKey, Object> idempotencyLocks = new ConcurrentHashMap<>();
    private final Map<IdempotencyKey, CreatePaymentCommand> idempotencyRequests = new ConcurrentHashMap<>();

    // Fine-grained locks per payment ID for concurrent refund operations
    private final Map<PaymentId, Object> paymentLocks = new ConcurrentHashMap<>();

    public PaymentProcessingService(PaymentRepository paymentRepository,
                                    RefundRepository refundRepository,
                                    PaymentProviderRegistry providerRegistry,
                                    FeeCalculationService feeCalculationService,
                                    RetryPolicy retryPolicy,
                                    EventPublisher eventPublisher) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository, "PaymentRepository cannot be null");
        this.refundRepository = Objects.requireNonNull(refundRepository, "RefundRepository cannot be null");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "ProviderRegistry cannot be null");
        this.feeCalculationService = Objects.requireNonNull(feeCalculationService, "FeeCalculationService cannot be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "RetryPolicy cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "EventPublisher cannot be null");
    }

    public Payment processPayment(CreatePaymentCommand command) {
        Objects.requireNonNull(command, "Payment command cannot be null");
        Object lock = idempotencyLocks.computeIfAbsent(command.idempotencyKey(), k -> new Object());

        synchronized (lock) {
            // Check existing idempotency record
            Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
            if (existingOpt.isPresent()) {
                CreatePaymentCommand initialCmd = idempotencyRequests.get(command.idempotencyKey());
                if (initialCmd != null && !command.isEquivalentTo(initialCmd)) {
                    throw new IdempotencyConflictException(
                            String.format("Idempotency key '%s' was previously used with a different request payload",
                                    command.idempotencyKey().value())
                    );
                }
                LOGGER.info(() -> "Returning existing payment for idempotency key: " + command.idempotencyKey().value());
                return existingOpt.get();
            }

            // Register request command
            idempotencyRequests.put(command.idempotencyKey(), command);

            // Calculate fees via Strategy
            Money fee = feeCalculationService.calculateFee(command.paymentMethod().getType(), command.amount());

            // Create domain aggregate
            PaymentId paymentId = PaymentId.generate();
            Payment payment = new Payment(
                    paymentId,
                    command.idempotencyKey(),
                    command.amount(),
                    fee,
                    command.paymentMethod(),
                    command.providerName()
            );

            paymentRepository.save(payment);

            eventPublisher.publish(new PaymentCreatedEvent(
                    payment.getId(),
                    payment.getIdempotencyKey(),
                    payment.getAmount(),
                    payment.getFee(),
                    payment.getPaymentMethod().getType().name(),
                    payment.getProviderName(),
                    Instant.now()
            ));

            // Execute provider processing with retries
            executePaymentWithRetries(payment);

            return payment;
        }
    }

    private void executePaymentWithRetries(Payment payment) {
        payment.markProcessing();
        PaymentProvider provider = providerRegistry.getRequiredProvider(payment.getProviderName());

        int attempt = 0;
        boolean terminal = false;

        while (!terminal) {
            attempt++;
            final int currentAttempt = attempt;
            try {
                LOGGER.info(() -> String.format("Executing payment %s with provider %s (attempt %d)",
                        payment.getId().value(), provider.getName(), currentAttempt));

                PaymentProvider.ProviderTransactionResponse response = provider.processPayment(payment);

                if (response.success()) {
                    payment.markSucceeded(response.providerTransactionId());
                    paymentRepository.save(payment);
                    eventPublisher.publish(new PaymentSucceededEvent(
                            payment.getId(),
                            payment.getAmount(),
                            payment.getProviderName(),
                            response.providerTransactionId(),
                            Instant.now()
                    ));
                    terminal = true;
                } else {
                    payment.recordTransaction(
                            PaymentStatus.PROCESSING,
                            PaymentStatus.PROCESSING,
                            payment.getProviderName(),
                            null,
                            String.format("Attempt %d failed: %s (%s)", currentAttempt, response.errorMessage(), response.errorCode()),
                            false
                    );

                    if (retryPolicy.shouldRetry(currentAttempt, response.retryable())) {
                        LOGGER.warning(() -> String.format("Payment %s attempt %d retryable failure. Backing off...",
                                payment.getId().value(), currentAttempt));
                        retryPolicy.backoff(currentAttempt);
                    } else {
                        payment.markFailed(response.errorMessage());
                        paymentRepository.save(payment);
                        eventPublisher.publish(new PaymentFailedEvent(
                                payment.getId(),
                                payment.getProviderName(),
                                response.errorMessage(),
                                Instant.now()
                        ));
                        terminal = true;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                payment.markFailed("Processing interrupted during retry backoff");
                paymentRepository.save(payment);
                eventPublisher.publish(new PaymentFailedEvent(
                        payment.getId(),
                        payment.getProviderName(),
                        "Interrupted during retry",
                        Instant.now()
                ));
                terminal = true;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Unexpected exception during payment processing", ex);
                payment.markFailed("Unexpected error: " + ex.getMessage());
                paymentRepository.save(payment);
                eventPublisher.publish(new PaymentFailedEvent(
                        payment.getId(),
                        payment.getProviderName(),
                        ex.getMessage(),
                        Instant.now()
                ));
                terminal = true;
            }
        }
    }

    public Refund processRefund(ProcessRefundCommand command) {
        Objects.requireNonNull(command, "Refund command cannot be null");
        Object paymentLock = paymentLocks.computeIfAbsent(command.paymentId(), k -> new Object());

        synchronized (paymentLock) {
            Payment payment = paymentRepository.findById(command.paymentId())
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found with ID: " + command.paymentId().value()));

            // Initiate refund on aggregate root (validates status and remaining refundable amount)
            Refund refund = payment.initiateRefund(command.refundId(), command.amount(), command.reason());
            refundRepository.save(refund);

            eventPublisher.publish(new RefundInitiatedEvent(
                    refund.getId(),
                    payment.getId(),
                    refund.getAmount(),
                    refund.getReason(),
                    Instant.now()
            ));

            PaymentProvider provider = providerRegistry.getRequiredProvider(payment.getProviderName());
            PaymentProvider.ProviderRefundResponse response = provider.processRefund(payment, refund);

            if (response.success()) {
                refund.markSucceeded(response.providerRefundId());
                payment.recordTransaction(
                        payment.getStatus(),
                        payment.getStatus(),
                        payment.getProviderName(),
                        response.providerRefundId(),
                        "Refund succeeded: " + refund.getAmount(),
                        true
                );
                refundRepository.save(refund);
                paymentRepository.save(payment);

                eventPublisher.publish(new RefundSucceededEvent(
                        refund.getId(),
                        payment.getId(),
                        refund.getAmount(),
                        response.providerRefundId(),
                        Instant.now()
                ));
            } else {
                refund.markFailed(response.errorMessage());
                payment.recordTransaction(
                        payment.getStatus(),
                        payment.getStatus(),
                        payment.getProviderName(),
                        null,
                        "Refund failed: " + response.errorMessage(),
                        false
                );
                refundRepository.save(refund);
                paymentRepository.save(payment);

                eventPublisher.publish(new RefundFailedEvent(
                        refund.getId(),
                        payment.getId(),
                        response.errorMessage(),
                        Instant.now()
                ));
                throw new InvalidRefundException("Provider refund rejected: " + response.errorMessage());
            }

            return refund;
        }
    }

    public Optional<Payment> getPayment(PaymentId paymentId) {
        return paymentRepository.findById(paymentId);
    }
}
