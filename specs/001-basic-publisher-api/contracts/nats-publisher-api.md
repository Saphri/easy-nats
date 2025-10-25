# API Contract: NatsPublisher (MVP 001)

**Version**: 1.0.0
**Status**: Stable for MVP 001
**Date**: 2025-10-26

## Overview

This document defines the API contract for the `NatsPublisher` class, which is the primary public interface for MVP 001. The interface is intentionally simple: a single method for publishing string messages to NATS.

---

## Public API

### NatsPublisher Class

**Package**: `io.quarkus.easynats`

**Scope**: `@Singleton` CDI bean (injected via `@Inject`)

**Visibility**: `public`

#### Constructor

No public constructor. Instance created and managed by Arc CDI container.

#### Methods

##### publish(String message)

```java
public void publish(String message) throws Exception
```

**Description**: Publishes a string message to the NATS subject `test`.

**Parameters**:
- `message` (String): The message content to publish as UTF-8 string
  - **Type**: String
  - **Nullable**: No (NullPointerException if null; not explicitly documented)
  - **Length**: Unbounded (limited only by NATS broker max message size, typically 1MB)

**Return Value**:
- `void` (fire-and-forget model)

**Throws**:
- `Exception` (checked exception)
  - `IOException`: Network error, broker unreachable (after startup), authentication failure
  - Other JetStream exceptions wrapped as IOException or thrown as-is

**Exceptions Details**:
| Exception | When | Caller Action |
|-----------|------|---------------|
| IOException | Network error during publish | Caller must catch and handle (retry, log, propagate) |
| IOException | Subject doesn't exist in stream | Caller must catch; indicates stream setup issue |
| IOException | Stream unreachable | Caller must catch; indicates broker problem |
| Exception (generic) | JNats library throws | Caller must catch and handle |

**Timing**: Synchronous (blocks until NATS broker acknowledges)

**Guarantees**:
- Message is sent to JetStream stream `test_stream`, subject `test`
- Message payload is raw UTF-8 bytes (no CloudEvents wrapping)
- Publish is atomic (succeeds fully or throws; no partial publish)

**Example Usage**:

```java
@Inject
NatsPublisher publisher;

public void sendNotification(String content) {
    try {
        publisher.publish(content);
    } catch (Exception e) {
        logger.error("Failed to publish message", e);
        // Handle error: retry, alert, or propagate
    }
}
```

---

## NatsConnectionManager Class (Internal, Not Public)

**Package**: `io.quarkus.easynats`

**Visibility**: package-private (used internally; not part of public API)

**Note**: Developers should NOT inject or use this class directly. Use `NatsPublisher` for all publishing.

---

## Behavioral Contracts

### Injection Contract

```java
@Inject
NatsPublisher publisher;
```

- **When**: After application startup completes; NatsConnectionManager initialization finished
- **Guarantee**: NatsPublisher instance is non-null and usable immediately
- **Timing**: NatsConnectionManager.@Observes StartupEvent completes synchronously before any @Inject fields populated

### Message Content Contract

- **Input**: Any non-null String (including empty string "")
- **Encoding**: UTF-8
- **Storage**: Stored in JetStream as raw bytes
- **Retrieval**: Receivers get exact same bytes; no transformation applied

### Connection Lifecycle Contract

- **Connection**: Created once at application startup
- **Scope**: Singleton per application instance
- **Failure**: If connection fails at startup, application aborts (fail-fast)
- **Reuse**: Same connection used for all publish operations (no per-call connection creation)

### Error Handling Contract

- **Failures**: Propagated to caller as Exception (caller responsible for handling)
- **No Retries**: publish() does not retry on failure; caller decides retry strategy
- **No Degradation**: No silent failures or degraded mode; all failures throw

---

## Backward Compatibility

This is MVP 001 API; backward compatibility not guaranteed for MVP 002+.

**Known Future Incompatibilities**:
- MVP 002 will add type generics: `NatsPublisher<T>` (current `NatsPublisher` remains for compatibility)
- MVP 002 will add `@NatsSubject` annotation (affects injection syntax)
- MVP 003 will add async variants: `publishAsync()` returning `CompletionStage`

---

## Testing Contract

### Integration Test Expectations

```java
@QuarkusTest
public class BasicPublisherTest {
    
    @Inject NatsPublisher publisher;
    
    @Test
    public void testPublisherCanBeInjected() {
        assertNotNull(publisher);
    }
    
    @Test
    public void testPublisherPublishesMessage() throws Exception {
        // Should not throw
        publisher.publish("hello");
    }
    
    @Test
    public void testMessageAppearsOnBroker() throws Exception {
        publisher.publish("hello world");
        
        // Subscribe via JetStream and verify message arrives
        JetStream js = natsConnection.jetStream();
        Subscription sub = js.subscribe("test", pullOptions().limit(1));
        Message msg = sub.nextMessage(Duration.ofSeconds(5));
        
        assertEquals("hello world", new String(msg.getData(), StandardCharsets.UTF_8));
        msg.ack();
    }
}
```

### Contract Validation Points

- [ ] Injection via `@Inject` succeeds
- [ ] publish() method exists and is callable
- [ ] publish("test message") does not throw on success
- [ ] Message appears on NATS subject `test`
- [ ] Message content is exact match (no modifications)
- [ ] Exception thrown on network failure (caller handles)
- [ ] Latency < 100ms p50 on localhost (SC-005)

---

## Non-Functional Requirements

| Aspect | Requirement | Notes |
|--------|-------------|-------|
| Latency | < 100ms p50 | End-to-end from publish() call to NATS ack |
| Thread Safety | Thread-safe (same instance used across threads) | @Singleton shared across application |
| Resource Cleanup | No resource leaks | NatsConnectionManager manages lifecycle |
| Memory Overhead | Minimal (no buffering, no state) | Fire-and-forget model |

---

## Known Limitations (MVP 001)

- [ ] No subject configuration (hardcoded to `test`)
- [ ] No message type safety (untyped string only)
- [ ] No CloudEvents support
- [ ] No async publishing
- [ ] No publish acknowledgment details (fire-and-forget only)
- [ ] No message filtering or transformation
- [ ] No metrics/observability integration

See `deferred-error-handling.md` and `plan.md` for future roadmap.
