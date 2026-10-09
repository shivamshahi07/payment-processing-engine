# Design Decisions: Payment Processing Engine

## 1. Design Patterns Implemented

| Pattern | Implementation | Problem Solved |
|---|---|---|
| **Strategy** | `FeeCalculationStrategy`, `CardFeeCalculationStrategy`, `UpiFeeCalculationStrategy` | Separates fee computation logic by payment instrument without `switch-case` pollution in domain models. |
| **Adapter** | `FastPayProviderAdapter`, `SecureGatewayProviderAdapter` implementing `PaymentProvider` | Normalizes simulated or real third-party payment gateway contracts to a unified internal domain API. |
| **State Machine** | `PaymentStatus` and `Payment.java` | Prevents invalid mutations (e.g. attempting to pay or cancel an already succeeded payment). |
| **Observer** | `EventPublisher` and `EventSubscriber` | Decouples notification, audit logging, and metric emission from payment core logic. |
| **Registry / Factory** | `PaymentProviderRegistry` | Allows dynamic discovery and registration of new payment gateways without modifying orchestrators. |
| **Repository** | `PaymentRepository`, `RefundRepository` | Shields application logic from persistence mechanisms (in-memory vs database). |

---

## 2. Alternatives Considered & Trade-Offs

### Floating-Point vs BigDecimal
- **Decision**: Used `BigDecimal` with explicit currency scale and `RoundingMode.HALF_UP` inside an immutable `Money` value object.
- **Alternative**: `double` or `float`.
- **Reasoning**: Floating-point types cause rounding drift (e.g. `0.1 + 0.2 = 0.30000000000000004`), which is unacceptable in monetary systems.

### Fine-Grained Key Locking vs Global Synchronization
- **Decision**: Fine-grained lock per `IdempotencyKey` and `PaymentId`.
- **Alternative**: Synchronizing the entire `processPayment()` method.
- **Reasoning**: Global synchronization severely limits throughput, creating a bottleneck across unrelated users. Fine-grained locking ensures high concurrency while guaranteeing mutual exclusion on identical keys.

### Simulating Provider-Side Idempotency
- **Limitation**: In a pure simulation, network failures are simulated by exceptions.
- **Real-World Reality**: When a network timeout occurs between merchant and provider, the provider may have already charged the customer. In a real integration, the engine must support idempotency keys passed directly in the provider API request headers and query the transaction status before initiating retries.
