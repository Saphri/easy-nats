# Research: Explicit Ack/Nak Control

**Feature**: 009-explicit-ack-nak
**Date**: 2025-10-28
**Status**: Phase 0 Complete

## Overview

This document records design decisions and research findings for implementing explicit message acknowledgment and negative acknowledgment (nak) in the EasyNATS Quarkus extension. The feature builds on durable consumer support (008-durable-nats-consumers) by exposing fine-grained message control.

---

## 1. NatsMessage<T> Wrapper Design

### Decision
Implement `NatsMessage<T>` as a **thin typed wrapper** around the underlying NATS JetStream `Message` object.

### Rationale
- **Type safety**: Generic parameter `<T>` provides compile-time type safety for deserialized payloads
- **Minimal overhead**: Pass-through delegation for control methods (`ack()`, `nak()`, `term()`) avoids framework intervention
- **Transparency**: Developers can reason about behavior directly from NATS JetStream semantics
- **Simplicity**: No intermediate state management, validation layers, or complex lifecycle handling in the framework

### Design Details

**Core Methods**:
- `ack()`: Direct pass-through to underlying NATS message's ack() method
- `nak(Duration delay)`: Direct pass-through to underlying NATS message's nak() method
- `term()`: Direct pass-through to underlying NATS message's term() method
- `payload()`: Returns deserialized typed payload (`T`)
- Header access: Direct pass-through to underlying NATS message headers

**No Framework Intervention**:
- Idempotency: Guaranteed by NATS JetStream (re-acking same message is safe)
- Error handling: NATS rejects invalid ack/nak/term at broker level
- Context validation: No async enforcement; developer responsibility
- Thread safety: Direct delegation to JNats client library (which handles thread-safety)

### Alternatives Considered
1. **Framework-level ack/nak wrapper with validation**: Would add complexity; NATS already validates at broker. Rejected because it introduces unnecessary state management.
2. **Builder pattern for ack/nak operations**: Overly complex for simple pass-through. Rejected in favor of direct method calls.
3. **Static ack/nak context**: Would require thread-local storage. Rejected because `NatsMessage` parameter is simpler and more explicit.

---

## 2. Method Signature Pattern: Implicit vs. Explicit Control

### Decision
Message control mode is determined by **method parameter type**:
- **`NatsMessage<T> orderMsg`** → Explicit control (no auto-ack/nak; developer calls `orderMsg.ack()` etc.)
- **`Order order`** (typed payload only) → Implicit control (respects `autoAck` annotation property)

### Rationale
- **Clear intent**: Method signature itself declares control pattern—no ambiguity
- **Backward compatible**: Existing code using typed-only parameters continues to work (auto-ack by default)
- **Zero runtime overhead**: Control pattern determined at build time (annotation processing)
- **Prevents accidental misuse**: If developer needs explicit control, they must explicitly request `NatsMessage<T>` parameter

### Design Details

**Implicit Mode** (`autoAck` applies):
- `@NatsSubscriber(autoAck=true)` [default]: Framework auto-acks on method success, auto-naks on exception
- `@NatsSubscriber(autoAck=false)`: Framework does nothing; message left in pending state (developer responsible)

**Explicit Mode** (no auto-ack/nak):
- `@NatsSubscriber(autoAck=true or false)` [ignored]: Parameter is `NatsMessage<T>`; developer must call `ack()`, `nak()`, or `term()`
- No implicit behavior; `autoAck` setting is not applicable

### Alternatives Considered
1. **Separate `@ExplicitAck` annotation**: Adds annotation bloat. Rejected because parameter type is self-documenting.
2. **Configuration property to enable/disable explicit mode**: Introduces runtime complexity. Rejected in favor of compile-time parameter-driven approach.
3. **Optional parameter injection**: Less explicit; harder to discern intent. Rejected in favor of required parameter pattern.

---

## 3. No Framework Validation of AckPolicy

### Decision
Framework does **not** validate that durable consumer is configured with correct `AckPolicy`. Let NATS JetStream reject invalid configurations at runtime.

### Rationale
- **Simplicity**: Removes build-time or startup validation logic from framework
- **Single source of truth**: NATS is authoritative on consumer configuration; framework avoids duplicating validation
- **Runtime flexibility**: Configurations can be adjusted on broker without recompiling; validation happens when ack/nak/term is attempted
- **Developer clarity**: Errors come directly from NATS with clear semantics (not wrapped/reinterpreted by framework)

### Error Handling
When developer calls `nak()` on a message from a consumer with `AckPolicy=none`:
1. Framework delegates to NATS JetStream
2. NATS rejects the nak() operation (broker-level error)
3. Error propagates to developer code (framework transparent)
4. Developer handles exception or lets it bubble up

### Alternatives Considered
1. **Build-time validation via annotation processor**: Requires access to NATS broker config at build time (not always available). Rejected.
2. **Startup validation**: Adds latency and complexity. Better to validate on first actual ack/nak attempt.
3. **Silent failure (no-op nak)**: Confuses developers; explicit failure is better.

---

## 4. Async Usage and Developer Responsibility

### Decision
Framework provides API; **developers are solely responsible** for correct usage in async contexts. Framework does not enforce, validate, or support any async patterns.

### Rationale
- **Minimal scope**: Feature is about exposing ack/nak control, not managing async lifecycle
- **Developer choice**: Developers choose whether/how to use async; one-size-fits-all async support is too opinionated
- **Avoid hidden gotchas**: No magic thread-local storage, context propagation, or scope management hiding in the background
- **Testability**: Developers must write their own async tests; framework isn't responsible for verifying thread-safety of user code

### Constraints
- Calling `nak()` from an async callback after subscriber method returns: **Allowed but risky**; developer must ensure message context is valid
- Calling `ack()` twice from separate async tasks: **Not prevented**; idempotency guaranteed by NATS, but race conditions are developer's problem
- Using `NatsMessage` after async operation completes: **Developer responsibility**; message reference may become invalid depending on NATS broker lifecycle

### Examples of Developer-Responsible Patterns
```java
// Pattern 1: Explicit error handling with async
@NatsSubscriber(subject = "orders")
void processOrder(NatsMessage<Order> msg) {
    executorService.submit(() -> {
        try {
            handleOrder(msg.payload());
            msg.ack();  // OK: ack() is thread-safe via NATS
        } catch (Exception e) {
            msg.nak(Duration.ofSeconds(5));  // OK: nak() is thread-safe via NATS
        }
    });
}

// Pattern 2: Conditional ack/nak based on external service
@NatsSubscriber(subject = "orders")
void processOrder(NatsMessage<Order> msg) {
    CompletableFuture.supplyAsync(() -> validateWithExternalService(msg.payload()))
        .thenAccept(isValid -> {
            if (isValid) msg.ack();
            else msg.nak(Duration.ofSeconds(30));
        });
}
```

### Alternatives Considered
1. **Async scope management (e.g., `@Async`-aware context)**: Introduces complexity; not in scope for MVP. Deferred for future feature.
2. **Built-in retry handler**: Would be opinionated; developers should implement via nak + consumer config. Deferred.
3. **Dead-letter topic auto-routing**: Would require consumer configuration changes; out of scope. Deferred.

---

## 5. nak() Redelivery Delay Semantics

### Decision
Framework **delegates delay handling entirely to NATS JetStream**. The `nak(Duration delay)` method passes the delay directly to the underlying NATS message; NATS determines actual redelivery timing based on consumer configuration.

### Rationale
- **NATS is authoritative**: NATS JetStream has sophisticated delay/backoff logic (max redelivery attempts, AckPolicy settings, etc.)
- **No reimplementation**: Framework avoids duplicating NATS scheduling logic
- **Transparency**: Developers understand delay behavior through NATS documentation, not framework docs
- **Flexibility**: NATS can change delay semantics without framework intervention

### Delay Parameter Semantics
- `nak()`: Redelivers with NATS consumer's configured delay
- `nak(Duration.ofSeconds(5))`: Requests 5-second delay; NATS may honor or override based on consumer config
- `nak(Duration.ZERO)`: Immediate redelivery; NATS may still apply minimum delay

### Alternatives Considered
1. **Framework-managed retry queue**: Would require stateful storage; out of scope. Deferred.
2. **Built-in exponential backoff**: Framework could compute delay, but NATS already supports this via consumer config. Rejected in favor of NATS config.
3. **Custom delay strategy interface**: Adds complexity; direct NATS delegation is simpler. Rejected.

---

## 6. Idempotency Guarantees

### Decision
Calling `ack()`, `nak()`, or `term()` multiple times on the same `NatsMessage<T>` is guaranteed to be **idempotent** (safe and side-effect-free on subsequent calls).

### Rationale
- **NATS provides idempotency**: JetStream handles duplicate ack/nak gracefully; second ack on already-acked message is a no-op
- **Error resilience**: Developers can call `ack()` with confidence even if exception might be thrown afterward
- **Predictable behavior**: Aligns with Kubernetes-style idempotent operations

### Implementation
- Framework relies on NATS JetStream's built-in idempotency
- No additional state tracking needed in `NatsMessage<T>` wrapper
- Multiple calls to `ack()` from same message instance → NATS handles gracefully

### Testing Requirement
- Unit tests MUST verify idempotency: call `msg.ack()` twice; both calls succeed with no error
- Integration tests MUST verify message is not redelivered after duplicate ack

---

## 7. Exception Handling After Explicit Control

### Decision
If a subscriber method calls `ack()` or `nak()` and then throws an exception:
1. The ack/nak action **has already taken effect** (at NATS broker level)
2. Exception propagates normally; framework does **not** undo the ack/nak

### Rationale
- **Transaction semantics**: Once `ack()` returns, message is delivered (at broker level); reverting would require distributed transactions (out of scope)
- **Simplicity**: No rollback logic; exceptions are just exceptions
- **Developer control**: Developer can suppress exceptions if needed (`try-catch` in subscriber method)

### Example
```java
@NatsSubscriber(subject = "orders")
void processOrder(NatsMessage<Order> msg) {
    msg.ack();  // Message is now marked delivered at broker
    throw new RuntimeException("Oops!");  // Exception occurs AFTER ack
    // Message is NOT redelivered despite exception
}
```

### Alternatives Considered
1. **Automatic nak on exception**: Would override developer's explicit ack; rejected because ack semantics must be honored
2. **Rollback via term()**: Would require complex state management; out of scope
3. **Warning log on exception-after-ack**: Nice-to-have; can be added later without changing spec

---

## 8. NatsMessage Parameter in Existing Code

### Decision
Existing code using typed-only parameters (e.g., `void handle(Order order)`) is **unaffected**. The `NatsMessage<T>` parameter is purely **opt-in**.

### Rationale
- **Backward compatibility**: No breaking changes to existing `@NatsSubscriber` methods
- **Opt-in adoption**: Developers upgrade to explicit control only when needed
- **Annotation-driven pattern**: Quarkus ecosystem encourages opt-in via method signature

### Migration Path for Developers
1. Start with implicit mode: `void handle(Order order)` + `@NatsSubscriber(subject = "orders")`
2. If advanced control needed: Change to `void handle(NatsMessage<Order> msg)` (no annotation change needed)
3. Call `msg.ack()`, `msg.nak()`, or `msg.term()` as needed

### Alternatives Considered
1. **Require `NatsMessage<T>` for all subscribers**: Breaking change; rejected
2. **Support both in same method**: Signature ambiguity; rejected
3. **Gradual migration via property flag**: Unnecessary complexity; rejected

---

## Summary of Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Wrapper Type** | Thin typed wrapper | Type safety + minimal overhead |
| **Control Mode Signal** | Method parameter type | Clear intent; compile-time determination |
| **Auto-Ack Pattern** | Parameter-driven | Backward compatible; no ambiguity |
| **AckPolicy Validation** | None (NATS validates) | Simplicity; NATS is authoritative |
| **Async Support** | Developer responsibility | Minimal scope; avoid opinionated enforcement |
| **Nak Delay** | Direct delegation to NATS | Transparency; leverage NATS scheduling |
| **Idempotency** | Guaranteed by NATS | Built-in; no additional state tracking |
| **Exception Handling** | No rollback | Transaction semantics out of scope |
| **Backward Compatibility** | Full (opt-in feature) | Existing code unaffected |

---

## Phase 0 Status: ✅ Complete

All design decisions documented. No NEEDS CLARIFICATION items remain. Ready for Phase 1 (data model, contracts, quickstart).
