# Roadmap: Payment Processing Engine

## Planned Enhancements

1. **Distributed Idempotency Store (Redis / DynamoDB)**
   - Replace in-memory concurrent maps with Redis SETNX / distributed lock leases.

2. **Webhooks & Asynchronous Provider Callbacks**
   - Ingest asynchronous gateway webhooks (e.g. 3D-Secure card verification or async UPI transfers).

3. **Multi-Currency FX Conversion**
   - Integrate foreign exchange conversion strategies with real-time rate providers.

4. **Circuit Breaker Pattern**
   - Automatically fast-fail requests to unhealthy third-party gateways using a Resilience4j-style circuit breaker.
