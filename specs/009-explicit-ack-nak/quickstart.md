# Quickstart: Explicit Ack/Nak Control

**Feature**: 009-explicit-ack-nak
**Date**: 2025-10-28
**Status**: Phase 1 Design

---

## Overview

This guide introduces developers to explicit message acknowledgment and negative acknowledgment (nak) in the EasyNATS Quarkus extension. Use this feature when you need fine-grained control over message delivery, such as:

- Conditional acknowledgment based on processing logic
- Implementing retry patterns with custom delays
- Advanced error handling (transient vs. permanent failures)
- Dead-letter queue routing
- Message-level observability and tracing

---

## Prerequisites

1. Quarkus 3.27.0+ application with EasyNATS extension dependency
2. NATS JetStream server running (with durable consumer pre-configured)
3. Feature 008-durable-nats-consumers implemented
4. Familiarity with `@NatsSubscriber` annotation basics

---

## Concepts

### Implicit vs. Explicit Control

**Implicit Control** (Simple):
```java
@NatsSubscriber(subject = "orders")
void handleOrder(Order order) {
    // Framework automatically acks on success, naks on exception
    processOrder(order);
}
```

**Explicit Control** (Advanced):
```java
@NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "processor")
void handleOrder(NatsMessage<Order> msg) {
    // You decide when/how to ack/nak
    Order order = msg.payload();
    try {
        processOrder(order);
        msg.ack();  // Explicit acknowledgment
    } catch (Exception e) {
        msg.nak(Duration.ofSeconds(5));  // Request redelivery
    }
}
```

**How to Switch**:
- Change parameter from `Order order` to `NatsMessage<Order> msg`
- Call `msg.payload()` to get the typed data
- Call `msg.ack()`, `msg.nak()`, or `msg.term()` explicitly

---

## Example 1: Basic Acknowledgment

**Scenario**: Process an order and mark it delivered.

```java
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;

@ApplicationScoped
public class OrderProcessor {

    @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
    public void handleOrder(NatsMessage<Order> msg) {
        Order order = msg.payload();

        System.out.println("Processing order: " + order.id());

        // Process the order (could take time)
        saveOrderToDatabase(order);
        sendConfirmationEmail(order);

        // Acknowledge: message is now marked delivered
        // Message will NOT be redelivered even if app restarts
        msg.ack();
    }

    private void saveOrderToDatabase(Order order) {
        // Database logic here
    }

    private void sendConfirmationEmail(Order order) {
        // Email logic here
    }
}
```

**Key Points**:
- `msg.payload()` returns the deserialized Order object
- `msg.ack()` marks the message as delivered
- If app crashes after `msg.ack()`, message is NOT redelivered

---

## Example 2: Error Handling with Conditional Ack/Nak

**Scenario**: Handle transient errors with retry, permanent errors with logging.

```java
@ApplicationScoped
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
    public void handleOrder(NatsMessage<Order> msg) {
        Order order = msg.payload();

        try {
            // Attempt processing
            validateOrder(order);
            saveOrder(order);

            // Success: acknowledge
            msg.ack();
            log.info("Order processed: {}", order.id());

        } catch (NetworkException e) {
            // Transient error (network timeout, temporary DB unavailability)
            // Request redelivery after 5 seconds
            msg.nak(Duration.ofSeconds(5));
            log.warn("Transient error processing order {}: {}", order.id(), e.getMessage());

        } catch (ValidationException e) {
            // Permanent error (invalid order data, business rule violation)
            // Acknowledge to prevent retries; log for manual review
            msg.ack();
            log.error("Permanent error processing order {}: {}", order.id(), e.getMessage());

        } catch (Exception e) {
            // Unknown error: safe default is to retry
            msg.nak(Duration.ofSeconds(10));
            log.error("Unknown error processing order {}: {}", order.id(), e, e);
        }
    }

    private void validateOrder(Order order) throws ValidationException {
        if (order.total() < 0) {
            throw new ValidationException("Order total cannot be negative");
        }
    }

    private void saveOrder(Order order) throws Exception {
        // Database save logic
    }
}
```

**Key Points**:
- `msg.nak(Duration.ofSeconds(5))` requests redelivery after 5 seconds
- `msg.ack()` after error logs the issue but prevents retry (for unrecoverable errors)
- Different error types get different handling

---

## Example 3: Accessing Message Metadata and Headers

**Scenario**: Use CloudEvents headers and NATS metadata for tracing and observability.

```java
@ApplicationScoped
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    @NatsSubscriber(subject = "events.*", stream = "EVENTS", consumer = "event-processor")
    public void handleEvent(NatsMessage<Event> msg) {
        Event event = msg.payload();

        // Extract CloudEvents headers
        String traceId = msg.headers().get("traceparent");  // W3C Trace Context
        String eventType = msg.headers().get("ce-type");    // CloudEvents type
        String eventSource = msg.headers().get("ce-source");

        // Access NATS metadata
        String subject = msg.subject();
        MessageMetadata metadata = msg.metadata();
        int redeliveryCount = metadata.redeliveryCount;
        long streamSeq = metadata.sequence.stream;

        log.info("Event received: type={}, source={}, subject={}, traceId={}, redeliveries={}",
            eventType, eventSource, subject, traceId, redeliveryCount);

        try {
            processEvent(event, traceId, eventType);
            msg.ack();
        } catch (Exception e) {
            if (redeliveryCount < 3) {
                // Retry if we haven't exceeded attempt limit
                msg.nak(Duration.ofSeconds(5 * (redeliveryCount + 1)));  // Exponential backoff
            } else {
                // Max retries exceeded: acknowledge to move on
                msg.ack();
                log.error("Max retries exceeded for event: type={}, streamSeq={}", eventType, streamSeq);
            }
        }
    }

    private void processEvent(Event event, String traceId, String eventType) throws Exception {
        // Event processing logic
    }
}
```

**Key Points**:
- `msg.headers().get("ce-*")` accesses CloudEvents attributes
- `msg.metadata().redeliveryCount` tracks retry attempts
- `msg.subject()` identifies the source subject
- Combine redelivery count with nak delay for exponential backoff

---

## Example 4: Async Processing Pattern

**Scenario**: Offload processing to a background thread or async executor.

```java
@ApplicationScoped
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    @Inject
    @Named("asyncOrderExecutor")
    ExecutorService executor;

    @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
    public void handleOrder(NatsMessage<Order> msg) {
        Order order = msg.payload();

        // Offload processing to background thread
        executor.submit(() -> {
            try {
                processOrderAsync(order);
                msg.ack();  // Ack in background thread (thread-safe)
                log.info("Order processed asynchronously: {}", order.id());
            } catch (Exception e) {
                msg.nak(Duration.ofSeconds(5));
                log.error("Async processing failed for order {}: {}", order.id(), e.getMessage());
            }
        });
    }

    private void processOrderAsync(Order order) throws Exception {
        // Long-running process (API call, ML inference, etc.)
        Thread.sleep(5000);  // Simulate work
    }
}
```

**Key Points**:
- `msg.ack()` and `msg.nak()` are thread-safe (delegated to NATS client)
- Framework does NOT enforce async scope; developer is responsible for correctness
- Keep message reference (`msg`) alive while async operation is in-flight

---

## Example 5: Combining with Other Quarkus Features

**Scenario**: Use Quarkus transaction management alongside explicit ack/nak.

```java
@ApplicationScoped
public class OrderProcessor {

    @Inject
    OrderRepository orderRepo;

    @Inject
    NotificationService notificationService;

    @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
    @Transactional  // Quarkus transaction management
    public void handleOrder(NatsMessage<Order> msg) {
        Order order = msg.payload();

        try {
            // Save within transaction
            orderRepo.save(order);

            // Send notification (may fail independently)
            notificationService.sendConfirmation(order);

            // Explicit ack: database transaction is committed before ack
            msg.ack();

        } catch (DataIntegrityViolationException e) {
            // Database constraint violation: permanent error
            msg.ack();  // Ack despite error
            log.error("Database error, order discarded: {}", order.id());

        } catch (Exception e) {
            // Transient error: transaction will rollback, message will be redelivered
            msg.nak(Duration.ofSeconds(5));
            // Quarkus @Transactional will rollback the database changes
            throw e;  // Or handle gracefully
        }
    }
}
```

**Key Points**:
- `@Transactional` works alongside explicit ack/nak
- Call `msg.ack()` only after transaction commits (or after handling rollback)
- Understand your transaction boundaries before acking

---

## Common Patterns

### Pattern: Exponential Backoff

```java
int redeliveryCount = msg.metadata().redeliveryCount;
long delaySeconds = 1L << Math.min(redeliveryCount, 5);  // 1, 2, 4, 8, 16, 32 seconds
msg.nak(Duration.ofSeconds(delaySeconds));
```

### Pattern: Dead-Letter Handling

```java
if (msg.metadata().redeliveryCount >= 3) {
    // Max retries: move to dead-letter queue
    publishToDeadLetterTopic(msg.payload());
    msg.ack();
} else {
    msg.nak(Duration.ofSeconds(5));
}
```

### Pattern: Conditional Processing

```java
Order order = msg.payload();
if (shouldProcess(order)) {
    processOrder(order);
    msg.ack();
} else {
    // Skip this message (still acknowledge to remove from queue)
    msg.ack();
    log.info("Skipped order: {}", order.id());
}
```

---

## Troubleshooting

### "nak() call fails with JetStreamApiException"

**Cause**: Consumer is not configured with `AckPolicy=explicit`.

**Fix**: Ensure durable consumer is created with:
```bash
nats consumer add ORDERS order-processor --ack explicit
```

### "msg.ack() succeeds but message is redelivered after restart"

**Cause**: Consumer configuration or broker state issue.

**Check**:
1. Verify consumer exists: `nats consumer list ORDERS`
2. Verify consumer settings: `nats consumer info ORDERS order-processor`
3. Check app is using correct stream/consumer names in `@NatsSubscriber`

### "payload() throws DeserializationException"

**Cause**: Message payload cannot be deserialized to the expected type.

**Fix**:
1. Verify message payload is valid JSON for the type
2. Check if message was published with correct schema
3. Use `msg.data()` to inspect raw bytes if needed

### "Calling ack() from async callback doesn't work"

**Cause**: Async operation may outlive message context.

**Workaround**:
1. Ensure message reference stays alive during async operation
2. Test with small message volumes first
3. Add explicit error handling in async callbacks

---

## Next Steps

1. **Setup Test Environment**: Configure NATS JetStream with durable consumer
2. **Write Tests**: Create unit and integration tests for your subscriber methods
3. **Monitor**: Use observability features (W3C tracing, health checks) to monitor message processing
4. **Optimize**: Profile your subscriber methods; use async where appropriate
5. **Scale**: Monitor throughput and latency; adjust consumer concurrency and buffer sizes

---

## Related Documentation

- **Feature Spec**: See `spec.md` for complete feature specification
- **Data Model**: See `data-model.md` for entity definitions
- **API Contract**: See `contracts/nats-message-interface.md` for method signatures
- **Feature 008**: See `../008-durable-nats-consumers/` for durable consumer setup

---

## Phase 1 Status: ✅ Complete

Quickstart guide fully written. Ready for implementation planning and task generation.
