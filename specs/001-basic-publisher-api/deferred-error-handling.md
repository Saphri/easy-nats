# Deferred Error Handling & Lifecycle Strategy (MVP 001)

**Feature Branch**: `001-basic-publisher-api`
**Status**: Deferred for Future MVP
**References**: spec.md Edge Cases (L25–L28), Constraints (L67–L68)

## Scope of This Document

MVP 001 (Basic NatsPublisher) intentionally defers complex error-handling and lifecycle semantics to simplify the initial implementation and validate core architecture. This document records the deferred design decisions and forward-looking strategy for future MVPs.

## Deferred Scenarios

### 1. NATS Broker Unreachable at Startup

**Current MVP 001 Behavior**:
- `NatsConnectionManager` initializes on `@Observes StartupEvent` (Quarkus application startup)
- If NATS broker is unreachable, initialization **fails fast** with `IOException` or `ConnectException`
- Application startup fails; user sees error in logs
- **No automatic reconnection or degraded mode**

**Future Enhancement Opportunity (MVP 002+)**:
- Implement configurable startup behavior:
  - **Fail-fast mode** (current MVP 001): Crash on unreachable broker (suitable for production)
  - **Tolerant mode**: Log warning, allow app to start, fail only on first publish attempt
  - **Retry mode**: Attempt N retries with exponential backoff before failing
- Configuration property: `quarkus.easynats.startup-mode=fail-fast|tolerant|retry`
- If tolerant/retry, implement lazy connection initialization (create JetStream connection on first publish, not at startup)
- Rationale: Different deployment contexts require different strategies (Kubernetes expects fail-fast; legacy systems may prefer graceful degradation)

**Acceptance Criteria for Future MVP**:
- [ ] Configurable startup behavior via application.properties
- [ ] Metrics for connection retry attempts
- [ ] Health check reports graceful degradation state (if applicable)
- [ ] Documentation of trade-offs (fail-fast vs. tolerant vs. retry)

---

### 2. Publisher Called Before NATS Connection Established

**Current MVP 001 Behavior**:
- `NatsConnectionManager.getJetStream()` assumes connection is already initialized
- If publisher is called during Quarkus startup (race condition edge case), behavior is undefined
- **Synchronous blocking guarantee**: Connection MUST be established before application startup completes
- Quarkus startup event processing enforces single-threaded initialization order

**Future Enhancement Opportunity (MVP 002+)**:
- Document and enforce connection initialization timing:
  - Explicit startup sequencing via `@Priority` or `@Observes(priority=...)` on connection initialization
  - Verify no @Inject consumers of `NatsPublisher` execute before connection ready
  - Add startup latch/CountDownLatch to block injection until connection established
- Implement non-blocking/async publish variants:
  - `publish(String message)` remains synchronous (MVP 001)
  - `publishAsync(String message)` returns `CompletionStage<PublishAck>` for non-blocking flows
  - Rationale: Some applications need fire-and-forget semantics; async variants reduce blocking
- Add timeout/deadline support:
  - `publish(String message, Duration timeout)` for per-call timeout control
  - Configuration property: `quarkus.easynats.publish-timeout=5s` (default)

**Acceptance Criteria for Future MVP**:
- [ ] Explicit documentation of startup sequencing guarantees
- [ ] Async publish methods with clear semantics
- [ ] Per-call timeout support
- [ ] Integration test validating race-condition safety

---

## Deferred Features from Constitution Principles

### From Principle VI (Developer Experience First) – Deferred to MVP 002+

**Not Implemented in MVP 001**:
- `@NatsSubject` annotation for subject specification
- Generic type parameter `NatsPublisher<T>` for compile-time type safety
- `@NatsSubscriber` annotation for declarative subscribers
- Typed publish operations with schema validation

**Forward-Looking Design**:
MVP 002 will introduce annotation-driven, type-safe API:
```java
@Inject
@NatsSubject("orders")
NatsPublisher<Order> orderPublisher;

// Compile-time type safety; IDE autocomplete support
orderPublisher.publish(new Order(...)); // Order type enforced at compile-time
```

**Migration Path from MVP 001 → MVP 002**:
- Existing untyped `NatsPublisher` code remains functional (backward compatible)
- New code can use typed variant alongside legacy code
- Gradual migration path for users: deprecation warnings guide upgrade (MVP 003+)

---

### From Principle VII (Observability First) – Deferred to MVP 003+

**Not Implemented in MVP 001**:
- W3C Trace Context header propagation (`traceparent`, `tracestate`)
- Health checks for connection status
- Metrics: publish latency, error counts, connection state

**Forward-Looking Design**:
MVP 003 will add observability:
```java
// Health check integration
GET /q/health/ready → { "nats": "UP|DOWN" }

// Tracing integration
publish("message") → generates span with W3C traceparent header
→ NATS broker forwards to subscriber with header intact
→ subscriber continues trace chain
```

**Why Deferred from MVP 001?**:
- Core publishing works without tracing; observability is enhancement, not blocker
- Quarkus health/micrometer integration requires additional test infrastructure
- Deferral allows focus on simple, working MVP before adding operational complexity

---

## Implementation Guidance for Future MVPs

### Design Reviews Checklist

Before implementing deferred error-handling features, ensure:

- [ ] **Backward Compatibility**: New patterns don't break MVP 001 code
- [ ] **Configuration Clarity**: New config properties documented with examples
- [ ] **Test Coverage**: Add integration tests for each scenario (fail-fast, tolerant, retry modes)
- [ ] **Documentation**: Update quickstart.md with error-handling examples
- [ ] **Principle Alignment**: Verify against constitution principles (especially Principle VI, VII)

### Recommended MVP Sequence

1. **MVP 001** (current): Untyped publisher, fail-fast only, no observability ✅
2. **MVP 002**: Add `@NatsSubject` annotations, typed generics, async publish, retry modes
3. **MVP 003**: Add W3C tracing, health checks, metrics; complete Principle VII
4. **MVP 004+**: Subscriber feature, CloudEvents support, advanced consumer strategies

---

## References

- **spec.md**: Edge Cases (L25–L28), Constraints (L67–L68)
- **constitution.md**: Principle VI (Developer Experience First), Principle VII (Observability First)
- **tasks.md**: Phase 1 (Setup), Phase 2 (Foundational)
