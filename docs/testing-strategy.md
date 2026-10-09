# Testing Strategy: Payment Processing Engine

## 1. Overview
The testing suite verifies all layers of the application across unit, integration, edge-case, and concurrency tests.

| Test Class | Focus |
|---|---|
| `PaymentDomainTest` | Aggregate root invariants, status machine validation, refund balance deductions. |
| `MoneyTest` | High-precision arithmetic, scale enforcement, cross-currency rejection. |
| `ValueObjectsAndEnumsTest` | `PaymentId`, `RefundId`, `IdempotencyKey`, masking algorithms on card/UPI. |
| `InfrastructureComponentsTest` | Fee strategies, exponential backoff calculations, provider registries. |
| `PaymentProcessingServiceTest` | End-to-end integration: idempotency replay, retry transitions, event publishing. |
| `PaymentRefundAndEdgeTest` | Concurrent refund race conditions, double-spend prevention, provider decline. |

## 2. Concurrency Verification
- Used `CountDownLatch` and multi-threaded `ExecutorService` pools (4-8 threads) to simulate real-world race conditions on:
  - Concurrent requests with identical idempotency keys.
  - Concurrent refunds competing for the remaining refundable balance.
- Thread safety verified without flaky `sleep()` dependencies.
