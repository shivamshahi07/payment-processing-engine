# Architecture Overview: Payment Processing Engine

## 1. Architectural Layers

The Payment Processing Engine follows clean, layered architecture and hexagonal (Ports & Adapters) principles:

```
[ External Clients / CLI Main ]
               │
               ▼
   [ Application Services ] (PaymentProcessingService, FeeCalculationService)
       │              │
       ▼              ▼
  [ Ports ]     [ Domain Layer ] (Aggregates, Value Objects, State Rules)
       ▲
       │
 [ Infrastructure ] (FastPayProviderAdapter, SecureGatewayProviderAdapter,
                     InMemoryPaymentRepository, InMemoryRefundRepository,
                     ExponentialBackoffPolicy, InMemoryEventPublisher)
```

### Domain Layer (`com.shivamshahi.payments.domain`)
- **Entities & Aggregates**: `Payment` serves as the aggregate root governing transitions, refunds, and transactional history. `Refund` manages refund lifecycle invariants.
- **Value Objects**: Immutable instances of `Money` (holding `BigDecimal` and `Currency`), `PaymentId`, `RefundId`, and `IdempotencyKey`.
- **Enums**: `PaymentStatus` and `RefundStatus` with explicit `canTransitionTo()` and `isTerminal()` transition matrices.
- **Domain Events**: Immutable records (`PaymentCreatedEvent`, `PaymentSucceededEvent`, etc.) published upon state transitions.

### Application Layer (`com.shivamshahi.payments.application`)
- **Services**: `PaymentProcessingService` coordinates transaction orchestration, idempotency locking, provider dispatching, retries, and refunds.
- **Commands**: Immutable command DTOs (`CreatePaymentCommand`, `ProcessRefundCommand`).
- **Ports**: Interface contracts (`PaymentProvider`, `PaymentRepository`, `RefundRepository`, `FeeCalculationStrategy`, `RetryPolicy`, `EventPublisher`).

### Infrastructure Layer (`com.shivamshahi.payments.infrastructure`)
- Adapters implementing application ports without leaking external third-party classes into domain models.

---

## 2. Thread-Safety & Concurrency Strategy
1. **Per-Key Idempotency Locks**: `ConcurrentHashMap<IdempotencyKey, Object>` provides fine-grained lock striping so concurrent requests on different keys execute in parallel without contention, while duplicate requests with the same key serialize safely.
2. **Per-Payment Refund Locks**: `ConcurrentHashMap<PaymentId, Object>` serializes concurrent refund requests against the same payment, ensuring atomic validation of `getRemainingRefundableAmount()`.
3. **Immutable History**: Transaction records and domain events are immutable records, preventing state leakage to subscribers.
