# Data Model: Explicit Ack/Nak Control

**Feature**: 009-explicit-ack-nak
**Date**: 2025-10-28
**Phase**: 1 (Design)

---

## Overview

This document defines the data model, entities, and their relationships for the explicit ack/nak control feature. The model is centered on the `NatsMessage<T>` wrapper and its interaction with NATS JetStream messages.

---

## Core Entities

### 1. NatsMessage<T>

**Purpose**: Type-safe wrapper around NATS JetStream message with explicit control methods.

**Generics**:
- `T`: Type parameter for the deserialized message payload

**Fields**:

| Field | Type | Description | Immutable |
|-------|------|-------------|-----------|
| `underlyingMessage` | `io.nats.client.Message` | Underlying NATS JetStream message | Yes |
| `payload` | `T` | Pre-deserialized typed payload (deserialized at NatsMessage construction) | Yes |

**Public Methods**:

| Method | Return | Description | Semantics |
|--------|--------|-------------|-----------|
| `ack()` | `void` | Acknowledge the message to the broker | Pass-through to `underlyingMessage.ack()` |
| `nak(Duration delay)` | `void` | Negative acknowledge with optional redelivery delay | Pass-through to `underlyingMessage.nak(delay)` |
| `term()` | `void` | Explicitly terminate the message | Pass-through to `underlyingMessage.term()` |
| `payload()` | `T` | Get the pre-deserialized typed payload | Simple getter; returns pre-deserialized instance from construction time |
| `headers()` | `Headers` | Get message headers (CloudEvents attributes, etc.) | Pass-through to `underlyingMessage.getHeaders()` |
| `subject()` | `String` | Get the NATS subject name | Pass-through to `underlyingMessage.getSubject()` |
| `data()` | `byte[]` | Get raw message bytes | Pass-through to `underlyingMessage.getData()` |
| `metadata()` | `MessageMetadata` | Get NATS JetStream metadata (sequence, consumer, etc.) | Pass-through to `underlyingMessage.getMetaData()` |

**Idempotency**:
- `ack()` is idempotent: calling twice on same message is safe; second call is a no-op (NATS handles)
- `nak()` is idempotent: calling twice redelivers message once (NATS handles)
- `term()` behavior: NATS JetStream determines semantics

**State Transitions**:
```
RECEIVED → (ack | nak | term)
  │
  └─→ DELIVERED (via ack)
  └─→ REDELIVERING (via nak + delay)
  └─→ TERMINATED (via term)
```
*Note: State management is at NATS broker level; framework does not track state.*

**Thread Safety**:
- `NatsMessage<T>` itself is not thread-safe (references underlying non-thread-safe NATS message)
- However, `ack()`, `nak()`, `term()` delegate to NATS client library which is thread-safe
- Concurrent calls to these methods are **allowed** and safe (NATS handles)
- `payload()` is safe to call from multiple threads (returns pre-deserialized instance)

**Constraints**:
- NatsMessage construction fails if payload cannot be deserialized to type T; error thrown during construction
- `payload()` returns the pre-deserialized instance (no failures possible)
- `ack()` called after message context expires: NATS will reject (error propagates to developer)
- `nak()` called on message from non-durable consumer: NATS will reject
- Multiple `ack()`/`nak()` calls: First succeeds; subsequent calls are idempotent

---

### 2. Acknowledgment (Implicit)

**Purpose**: Framework-managed acknowledgment for implicit mode (when using typed-only parameter).

**Trigger**: Subscriber method completes successfully without throwing exception.

**Behavior**:
- Framework calls `message.ack()` (pass-through to underlying NATS message)
- Message is immediately marked delivered at broker
- No framework state tracking

**Error Handling**:
- If `ack()` call raises exception: Error is propagated; subscriber method exception handling should catch it
- Framework does not suppress or log these errors

---

### 3. Negative Acknowledgment (Implicit)

**Purpose**: Framework-managed redelivery request for implicit mode when subscriber method throws exception.

**Trigger**: Subscriber method throws any exception (checked or unchecked).

**Behavior**:
- Framework calls `message.nak(brokerConfiguredDelay)` (pass-through to underlying NATS message)
- Delay is determined by NATS JetStream consumer configuration; framework does not override
- Message is marked for redelivery; broker will redeliver after delay

**Error Handling**:
- If `nak()` call raises exception (e.g., invalid consumer config): Error is logged/propagated as framework error
- Original subscriber method exception is also logged/tracked

**Rollback Semantics**:
- If subscriber method calls `ack()` successfully, then throws exception: **No rollback**; message is delivered despite exception
- Idempotency: Subscriber method exception does not trigger additional nak

---

### 4. Message Metadata

**Purpose**: Structured access to NATS JetStream message properties beyond payload and headers.

**Source**: Delegated to underlying NATS `MessageMetadata` object.

**Fields** (accessible via `NatsMessage.metadata()`):

| Property | Type | Description |
|----------|------|-------------|
| `sequence.stream` | `long` | Sequence number within the stream |
| `sequence.consumer` | `long` | Sequence number within the consumer |
| `timestamp` | `Instant` | Message publish timestamp |
| `pending` | `PendingCount` | Remaining messages in consumer's pending list |
| `redeliveryCount` | `int` | Number of times this message has been redelivered |
| `consumerName` | `String` | Name of the durable consumer |

**Usage in Testing**:
- Verify sequence numbers to confirm delivery order
- Check redeliveryCount after nak to confirm retry behavior
- Validate pending count to ensure messages are being consumed

---

### 5. Exception Hierarchy

**Custom Exceptions**: Inherit from `JetStreamApiException` (NATS library exception).

**Scenarios**:
- `nak()` on non-durable consumer → NATS throws `JetStreamApiException`
- `ack()` on already-acked message → NATS no-op (no exception)
- Message context expired → NATS throws `IOException` or similar

**Framework Responsibility**: Pass through exceptions; do not wrap or reinterpret.

---

## Relationships

### NatsMessage<T> ↔ Subscriber Method

```
@NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
void handleOrder(NatsMessage<Order> msg) {
    Order order = msg.payload();
    try {
        processOrder(order);
        msg.ack();
    } catch (TransientError e) {
        msg.nak(Duration.ofSeconds(5));
    } catch (PermanentError e) {
        msg.ack();  // Ack and log; don't retry
        logger.error("Permanent error", e);
    }
}
```

**Cardinality**: 1 NatsMessage → 1 method invocation (1:1)

**Lifetime**: Message wrapper exists for duration of subscriber method execution + any async operations (developer's responsibility to keep valid)

---

## Validation Rules

### For Explicit Mode (NatsMessage<T>)

1. **Parameter must be NatsMessage<T>**: Framework inspects method signature at build time
2. **Type parameter T must be deserializable**: Jackson or Framework's deserialization engine must support T
3. **At least one control method must be called** (best practice, not enforced):
   - If developer calls none of ack/nak/term: Message handling deferred to NATS timeout behavior
   - Framework does not validate this; NATS determines what happens

### For Implicit Mode (Typed Payload)

1. **Parameter type must be deserializable**: Framework validates at build time
2. **autoAck annotation property is respected** (if present; default=true)

### At Runtime

1. **Message must originate from NATS JetStream**: Framework assumes all messages are valid NATS messages
2. **Broker must be reachable**: If broker unreachable, ack/nak will fail with IOException
3. **Consumer must exist** (for durable subscriptions): Enforced by feature 008; not re-validated here

---

## Lifecycle

### Message Lifecycle in Explicit Mode

```
1. Message received from NATS broker
2. Framework creates NatsMessage<T> wrapper
3. Subscriber method invoked with NatsMessage<T> parameter
4. Developer calls ack() / nak() / term() (or none)
5. Method returns
6. If developer called ack/nak/term: Message state updated at broker
7. If developer called none: NATS timeout policy applies
```

### Message Lifecycle in Implicit Mode

```
1. Message received from NATS broker
2. Framework creates NatsMessage<T> internally (hidden from developer)
3. Subscriber method invoked with typed payload parameter
4. Method completes (success or exception)
5. If success: Framework calls ack(); message marked delivered
6. If exception: Framework calls nak(); message marked for redelivery
7. Message state updated at broker
```

---

## Type System

### Generic Type Erasure

**Java Type Erasure**: Due to JVM type erasure, `NatsMessage<Order>` and `NatsMessage<String>` have same runtime type.

**Framework Handling**:
- Type information captured at annotation processing time (compile-time)
- Stored in `NatsSubscriber` annotation metadata
- `NatsMessage<T>` constructor is given type info (dependency injection)
- Deserialization happens in constructor using captured type info, not runtime generic parameter
- If deserialization fails, error is thrown from constructor; subscriber method is never invoked

**Example**:
```java
// At compile time: Framework knows T=Order
@NatsSubscriber(subject = "orders")
void handle(NatsMessage<Order> msg) {
    Order order = msg.payload();  // Uses Order class captured at compile time
}
```

---

## Data Volume & Scale

**Per-Message Overhead**:
- `NatsMessage<T>` wrapper: ~100 bytes (reference + underlying message reference)
- Deserialized payload: Already allocated at NatsMessage construction (depends on type T; Order might be 200-500 bytes)
- Total per-message memory: ~100 bytes (wrapper) + payload size

**Throughput Assumptions**:
- No bulk operations (feature handles one message at a time)
- Control methods (`ack()`, `nak()`) should complete in <10ms (delegated to NATS client)
- Throughput bottleneck is NATS broker, not framework wrapper

**Memory Assumptions**:
- Typical Quarkus app: 1-10 concurrent messages in-flight
- Memory footprint: Negligible (wrapper adds <1MB per 10k messages)

---

## Notes

1. **Deserialization at Construction**: `NatsMessage<T>` deserializes payload during construction, not on `payload()` call
2. **Early Failure Detection**: If payload cannot be deserialized, subscriber method is not invoked; error thrown during message construction
3. **No Custom State Tracking**: Framework does not maintain state machine or transaction log for messages
4. **Idempotency Boundary**: Idempotency guaranteed by NATS JetStream; framework is transparent
5. **Error Propagation**: All NATS exceptions propagate directly to subscriber method or caller
6. **Headers Immutable**: Message headers are read-only (from NATS); framework does not allow modification

---

## Phase 1 Status: ✅ Complete

Data model fully specified. Ready for API contracts and quickstart documentation.
