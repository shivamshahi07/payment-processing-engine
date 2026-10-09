# Payment Processing Engine

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/shivamshahi/payment-processing-engine)
[![Java Version](https://img.shields.io/badge/Java-17%20LTS-blue)](https://openjdk.org/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Coverage](https://img.shields.io/badge/coverage-89.5%25-brightgreen)](target/site/jacoco/index.html)

A production-grade, modular, in-memory payment processing engine written in Java demonstrating clean Low-Level Design (LLD), SOLID principles, gang-of-four design patterns, and thread-safe distributed systems concepts (idempotency, exponential backoff, state machines, and partial refunds).

---

## Resume Summary
- **Problem Solved**: Built an in-memory payment processing orchestration engine solving concurrency race conditions in payment creation, double refund exploitation, and transient gateway failures.
- **Key Design Concepts**: Strategy pattern for dynamic multi-currency fee calculations; Adapter pattern for payment gateways; State Machine enforcing strict lifecycle transitions; Fine-grained synchronized locks guaranteeing thread-safe idempotency; In-memory Observer pattern for decoupled domain events.
- **Outcomes**: Achieved **89.5% line coverage** across domain and application layers with zero external database or cloud dependencies, verified under heavy multithreaded contention tests.

---

## 1. Problem Statement
Payment infrastructure requires absolute correctness, transactional integrity, and resilience. Ordinary payment simulations often suffer from:
- Vulnerability to double-charge race conditions during simultaneous API requests with identical idempotency keys.
- Cumulative refund calculations vulnerable to concurrent race conditions.
- Ad-hoc error handling conflating retryable network hiccups with permanent card declines.
- Spaghetti orchestration coupling payment gateway specifics with domain entities.

This engine models a core payment platform that coordinates payment gateways, payment methods, transaction state transitions, exponential backoff retries, partial/full refunds, and decoupled audit events.

---

## 2. Key Features
- **Idempotency with Fine-Grained Locking**: Replaying equivalent requests returns the original payment; conflicting payloads using the same key trigger immediate conflict exceptions; concurrent requests never create duplicate charges.
- **Deterministic State Machine**: Strict lifecycle guards (`CREATED` → `PROCESSING` → `SUCCEEDED` / `FAILED` / `CANCELLED`). Terminal states cannot be mutated.
- **Extensible Gateway Adapters**: Pluggable provider contract supporting simulated gateways (`FastPay`, `SecureGateway`) with deterministic failure injection.
- **Monetary Arithmetic**: Dedicated `Money` value object utilizing `BigDecimal` with half-up rounding to prevent IEEE 754 floating-point errors.
- **Fee Calculation via Strategy Pattern**: Pluggable strategies for Credit/Debit Cards and UPI transactions.
- **Configurable Retries**: Exponential backoff with jitter and pluggable sleep abstractions for instant unit testing.
- **Full & Partial Refunds**: Strict balance tracking preventing cumulative refunds from exceeding payment totals.
- **Domain Event Publication**: Isolated in-memory pub-sub notifying audit observers without leaking mutable aggregate state.

---

## 3. Architecture & Design Patterns

### Architecture Overview
The system follows a clean, layered architecture separating domain invariants from infrastructure concerns:
```
com.shivamshahi.payments
├── domain/            # Entities, Value Objects, Enums, Domain Events, Domain Exceptions
│   ├── model/         # Payment (Aggregate Root), Refund, PaymentMethod
│   ├── valueobject/   # Money, PaymentId, RefundId, IdempotencyKey
│   └── enums/         # PaymentStatus, RefundStatus, PaymentMethodType
├── application/       # Use case orchestration, commands, and port interfaces
│   ├── command/       # CreatePaymentCommand, ProcessRefundCommand
│   ├── port/          # PaymentRepository, RefundRepository, PaymentProvider, RetryPolicy
│   └── service/       # PaymentProcessingService, FeeCalculationService
├── infrastructure/    # Concrete implementations of ports
│   ├── persistence/   # Concurrent in-memory repositories
│   ├── provider/      # FastPay and SecureGateway provider adapters
│   ├── fee/           # Card and UPI fee strategies
│   ├── retry/         # Exponential backoff and sleeper policies
│   └── event/         # Thread-safe in-memory event publisher
└── demo/              # Interactive CLI demo executing all 10 acceptance scenarios
```

### Design Patterns Used
1. **Strategy Pattern**: `FeeCalculationStrategy` encapsulates payment method fee logic.
2. **Adapter Pattern**: `PaymentProvider` normalizes disparate third-party gateway APIs into a unified domain protocol.
3. **State Pattern / Finite State Machine**: State transitions governed by domain methods on `Payment` and `Refund`.
4. **Observer Pattern**: `EventPublisher` publishes domain events to decoupled audit subscribers.
5. **Factory / Registry**: `PaymentProviderRegistry` enables dynamic provider lookup and extensibility.
6. **Repository Pattern**: `PaymentRepository` and `RefundRepository` abstract in-memory concurrent persistence.

---

## 4. Sequence Diagram: Payment Creation & Execution

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Service as PaymentProcessingService
    participant Repo as PaymentRepository
    participant Fee as FeeCalculationService
    participant Provider as PaymentProvider
    participant Events as EventPublisher

    Client->>Service: processPayment(CreatePaymentCommand)
    critical Idempotency Key Lock
        Service->>Repo: findByIdempotencyKey(key)
        alt Existing Request
            Service-->>Client: Return existing Payment
        else New Request
            Service->>Fee: calculateFee(method, amount)
            Fee-->>Service: fee Money
            Service->>Repo: save(Payment [CREATED])
            Service->>Events: publish(PaymentCreatedEvent)
            Service->>Service: markProcessing()
            loop Retryable Attempt <= maxAttempts
                Service->>Provider: processPayment(Payment)
                alt Success
                    Provider-->>Service: ProviderTransactionResponse(SUCCESS)
                    Service->>Service: markSucceeded(txnId)
                    Service->>Repo: save(Payment [SUCCEEDED])
                    Service->>Events: publish(PaymentSucceededEvent)
                else Retryable Failure
                    Provider-->>Service: ProviderTransactionResponse(RETRYABLE)
                    Service->>Service: recordTransaction(Attempt Failed)
                    Service->>Service: backoff(attempt)
                else Permanent Failure
                    Provider-->>Service: ProviderTransactionResponse(DECLINED)
                    Service->>Service: markFailed(reason)
                    Service->>Repo: save(Payment [FAILED])
                    Service->>Events: publish(PaymentFailedEvent)
                end
            end
            Service-->>Client: Payment
        end
    end
```

---

## 5. Prerequisites & Build Instructions

### Prerequisites
- **Java**: OpenJDK 17 LTS or higher
- **Maven**: Apache Maven 3.8+

### Build and Test Commands
```bash
# Clean, compile, and run all tests with JaCoCo verification
mvn clean verify

# Run only the test suite
mvn test

# Run the interactive 10-scenario CLI demo
java -cp target/classes com.shivamshahi.payments.Main
# Or via Maven exec
mvn compile exec:java -Dexec.mainClass="com.shivamshahi.payments.Main"
```

---

## 6. Sample Demo Output
```text
===========================================================================
   PAYMENT PROCESSING ENGINE - ACCEPTANCE SCENARIOS DEMO
===========================================================================
---------------------------------------------------------------------------
 Scenario 1: Successful Payment Processing (Card with FastPay)
---------------------------------------------------------------------------
   Payment ID: pay_0d8ef915cbf34613a26300a51fb02e03 | Status: SUCCEEDED | TxnId: fp_txn_... | Fee: USD 3.30
---------------------------------------------------------------------------
 Scenario 2: Permanent Payment Decline (No Retries on Business Decline)
---------------------------------------------------------------------------
   Payment ID: pay_5b9a34ddf3b4407bb2dcdb5531e2259a | Status: FAILED | Reason: Card declined by issuing bank
---------------------------------------------------------------------------
 Scenario 3: Retryable Provider Failure Recovered via Exponential Backoff
---------------------------------------------------------------------------
   Payment ID: pay_df4e000bdae649aa9aefea92b916b304 | Final Status: SUCCEEDED | TxnId: sg_txn_... | Total Attempts Recorded: 5
---------------------------------------------------------------------------
 Scenario 4: Safe Idempotent Replay (Same Key, Same Payload)
---------------------------------------------------------------------------
   Original Payment ID: pay_0d8ef915cbf34613a26300a51fb02e03 | Replay Payment ID: pay_0d8ef915cbf34613a26300a51fb02e03 | Identical: true
---------------------------------------------------------------------------
 Scenario 5: Conflicting Idempotency Request (Same Key, Different Payload)
---------------------------------------------------------------------------
   Expected Conflict Caught: Idempotency key 'idem-card-001' was previously used with a different request payload
---------------------------------------------------------------------------
 Scenario 6: Partial Refund Workflow
---------------------------------------------------------------------------
   Refund 1 ID: ref_f7e4... | Amount: USD 50.00 | Status: SUCCEEDED | Remaining Refundable: USD 100.00
   Refund 2 ID: ref_3bac... | Amount: USD 30.00 | Status: SUCCEEDED | Remaining Refundable: USD 70.00
---------------------------------------------------------------------------
 Scenario 7: Refund Exceeding Remaining Refundable Balance
---------------------------------------------------------------------------
   Expected Refund Error Caught: Requested refund of USD 100.00 exceeds remaining refundable amount USD 70.00
---------------------------------------------------------------------------
 Scenario 8: Invalid Payment State Transition Guard
---------------------------------------------------------------------------
   Expected State Machine Guard: Invalid payment state transition from SUCCEEDED to PROCESSING
---------------------------------------------------------------------------
 Scenario 9: Domain Event Publication Summary
---------------------------------------------------------------------------
   Total domain events observed by registered subscribers: 10
---------------------------------------------------------------------------
 Scenario 10: Concurrent Creation Race Prevention (Thread-Safe Idempotency)
---------------------------------------------------------------------------
   4 Concurrent threads executed for key 'idem-race-test-010'.
   All threads resolved to same Payment ID: pay_1b68032f531c4716bccf1133d59d65ea (Deduplicated = true)
===========================================================================
   ALL 10 PAYMENT PROCESSING ACCEPTANCE SCENARIOS PASSED SUCCESSFULLY!
===========================================================================
```

---

## 7. Limitations & Production Considerations
- **In-Memory Storage**: Current implementation utilizes `ConcurrentHashMap`. In high-scale distributed deployments, idempotency locks and records should be backed by Redis (e.g. Redlock algorithm) or database unique constraints with distributed leases.
- **Provider Side Idempotency**: In real-world payment integrations (e.g., Stripe, Adyen), network timeouts during payment calls necessitate querying provider transaction status APIs using client-supplied idempotency headers before executing blind retries.

---

## 8. License
This project is licensed under the MIT License - see the LICENSE file for details.
