# API Contract: NatsMessage<T> Interface

**Feature**: 009-explicit-ack-nak
**Date**: 2025-10-28
**Status**: Phase 1 Design

---

## Interface Definition

```java
/**
 * Type-safe wrapper around NATS JetStream message.
 * Provides explicit control over message acknowledgment via ack(), nak(), and term() methods.
 *
 * Developers use this parameter type in @NatsSubscriber methods to opt into explicit control:
 *
 * <pre>
 * @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "processor")
 * void handleOrder(NatsMessage<Order> msg) {
 *     Order order = msg.payload();
 *     try {
 *         processOrder(order);
 *         msg.ack();  // Explicit acknowledgment
 *     } catch (RetryableException e) {
 *         msg.nak(Duration.ofSeconds(5));  // Request redelivery after delay
 *     }
 * }
 * </pre>
 *
 * @param <T> Type of the deserialized message payload
 */
public interface NatsMessage<T> {

    /**
     * Get the deserialized message payload.
     *
     * Type T must be deserializable by the framework's message serialization engine (Jackson).
     * The payload is lazily deserialized on first call and cached; subsequent calls return the same instance.
     *
     * @return The deserialized payload of type T
     * @throws DeserializationException if payload cannot be deserialized to type T
     * @throws JsonMappingException if JSON structure doesn't match type T
     */
    T payload();

    /**
     * Acknowledge this message to the NATS JetStream broker.
     *
     * Once ack() returns successfully, the message is marked as delivered and will NOT be redelivered.
     * This call is pass-through to the underlying NATS JetStream message's ack() method.
     *
     * Idempotency: Calling ack() multiple times on the same message is safe; subsequent calls are no-ops.
     * Exception after ack: If this method completes successfully and then an exception is thrown later
     * in the subscriber method, the ack has already taken effect (no rollback).
     *
     * @throws IOException if the underlying NATS connection is broken
     * @throws JetStreamApiException if the broker rejects the ack (e.g., message already acked)
     */
    void ack();

    /**
     * Negative acknowledge (nak) this message, requesting redelivery after an optional delay.
     *
     * Once nak() returns successfully, the message is marked for redelivery and will be resent
     * to the consumer after the specified delay (or NATS broker's configured delay if none specified).
     * This call is pass-through to the underlying NATS JetStream message's nak(duration) method.
     *
     * Idempotency: Calling nak() multiple times on the same message is safe.
     * Delay handling: The provided delay is a hint to the broker; NATS may honor or override based
     * on consumer configuration (max redelivery attempts, backoff policy, etc.).
     *
     * @param delay Optional redelivery delay. If null or ZERO, NATS applies consumer's default delay.
     * @throws IOException if the underlying NATS connection is broken
     * @throws JetStreamApiException if the broker rejects the nak (e.g., non-durable consumer)
     */
    void nak(Duration delay);

    /**
     * Explicitly terminate this message without redelivery.
     *
     * This call is pass-through to the underlying NATS JetStream message's term() method.
     * Semantics depend on NATS consumer configuration.
     *
     * @throws IOException if the underlying NATS connection is broken
     * @throws JetStreamApiException if the broker rejects the term() call
     */
    void term();

    /**
     * Get the message headers (for accessing CloudEvents attributes and custom headers).
     *
     * Returns the underlying NATS message headers. CloudEvents attributes are stored as `ce-` prefixed headers.
     * This is a pass-through to the underlying NATS message's getHeaders() method.
     *
     * @return Headers object with all message metadata
     */
    Headers headers();

    /**
     * Get the NATS subject this message was published to.
     *
     * @return The subject name (e.g., "orders", "events.order.created")
     */
    String subject();

    /**
     * Get the raw message payload bytes.
     *
     * Use this if you need access to the raw serialized form (before deserialization).
     * This is a pass-through to the underlying NATS message's getData() method.
     *
     * @return Raw message bytes
     */
    byte[] data();

    /**
     * Get NATS JetStream message metadata (sequence numbers, redelivery count, etc.).
     *
     * Useful for testing and observability. This is a pass-through to the underlying
     * NATS message's getMetaData() method.
     *
     * @return MessageMetadata containing sequence, timestamp, redelivery count, etc.
     */
    MessageMetadata metadata();
}
```

---

## Usage Patterns

### Pattern 1: Simple Acknowledgment

```java
@NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
void handleOrder(NatsMessage<Order> msg) {
    Order order = msg.payload();
    processOrder(order);
    msg.ack();  // Always ack after successful processing
}
```

### Pattern 2: Error Handling with Conditional Ack/Nak

```java
@NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
void handleOrder(NatsMessage<Order> msg) {
    Order order = msg.payload();
    try {
        processOrder(order);
        msg.ack();
    } catch (TransientException e) {
        // Transient error: request redelivery
        msg.nak(Duration.ofSeconds(5));
    } catch (PermanentException e) {
        // Permanent error: ack to prevent retries, log the error
        msg.ack();
        logger.error("Permanent error processing order", e);
    }
}
```

### Pattern 3: Async Processing with Callback

```java
@NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
void handleOrder(NatsMessage<Order> msg) {
    Order order = msg.payload();

    // Process asynchronously (developer's responsibility for correctness)
    executor.submit(() -> {
        try {
            asyncProcessOrder(order);
            msg.ack();
        } catch (Exception e) {
            msg.nak(Duration.ofSeconds(10));
        }
    });
}
```

### Pattern 4: Accessing Headers and Metadata

```java
@NatsSubscriber(subject = "events", stream = "EVENTS", consumer = "event-processor")
void handleEvent(NatsMessage<Event> msg) {
    Event event = msg.payload();
    String traceId = msg.headers().get("traceparent");
    String correlationId = msg.headers().get("ce-correlationid");

    logger.info("Processing event: id={}, traceId={}, redeliveries={}",
        event.id(), traceId, msg.metadata().redeliveryCount);

    processEvent(event, traceId, correlationId);
    msg.ack();
}
```

---

## Method Contract Summary

| Method | Input | Output | Side Effects | Error Handling |
|--------|-------|--------|--------------|----------------|
| `payload()` | None | T (typed payload) | Deserializes on first call; caches result | Throws DeserializationException |
| `ack()` | None | void | Marks message delivered at broker | Throws IOException, JetStreamApiException |
| `nak(Duration)` | Optional delay | void | Marks message for redelivery after delay | Throws IOException, JetStreamApiException |
| `term()` | None | void | Terminates message (NATS-specific) | Throws IOException, JetStreamApiException |
| `headers()` | None | Headers | None (read-only) | None (immutable) |
| `subject()` | None | String | None (read-only) | None |
| `data()` | None | byte[] | None (read-only) | None |
| `metadata()` | None | MessageMetadata | None (read-only) | None |

---

## Implementation Notes

### For Framework Implementers

1. **NatsMessage<T> Creation**: Created during subscriber method invocation by framework
2. **Type Information**: Generic type T is captured from method signature during annotation processing
3. **Delegation**: All control methods (`ack()`, `nak()`, `term()`) delegate directly to underlying NATS `Message` object
4. **Caching**: Only `payload()` uses caching; all other methods are pure pass-throughs
5. **No State Tracking**: Framework does not maintain any state about message ack/nak status

### For Users/Developers

1. **Thread Safety**: Individual NatsMessage instance is not thread-safe, but ack/nak operations are thread-safe via NATS client
2. **Lifetime**: NatsMessage instance is valid for the duration of subscriber method + any async operations (developer responsible)
3. **Idempotency**: All control methods are idempotent; safe to call multiple times
4. **Error Handling**: Exceptions from ack/nak/term propagate to developer code; framework does not suppress

---

## Testing Requirements

### Unit Tests
- [ ] `payload()` deserializes correct type T
- [ ] `payload()` caches result (same instance returned on multiple calls)
- [ ] `ack()` calls underlying NATS message's ack()
- [ ] `nak(Duration)` calls underlying NATS message's nak(duration)
- [ ] `term()` calls underlying NATS message's term()
- [ ] Idempotency: calling `ack()` twice succeeds
- [ ] Idempotency: calling `nak()` twice succeeds
- [ ] `headers()`, `subject()`, `data()`, `metadata()` are pass-throughs

### Integration Tests
- [ ] Subscriber receives `NatsMessage<Order>` parameter
- [ ] Calling `msg.ack()` prevents message redelivery after restart
- [ ] Calling `msg.nak(Duration)` causes message redelivery after delay
- [ ] Calling `msg.nak()` (no delay) respects consumer's delay config
- [ ] Exception after `ack()` does not cause redelivery
- [ ] Multiple subscribers can ack/nak same message type concurrently

---

## Phase 1 Status: ✅ Complete

API contract fully specified. Ready for quickstart and implementation planning.
