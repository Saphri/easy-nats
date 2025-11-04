# Guide: Explicit Ack/Nak Control

This guide introduces explicit message acknowledgment (`ack`) and negative acknowledgment (`nak`) in the EasyNATS Quarkus extension. Use this feature for fine-grained control over message delivery, such as implementing retry patterns, handling transient vs. permanent failures, and building robust, error-tolerant systems.

---

## Overview

### Implicit vs. Explicit Control

By default, Quarkus EasyNATS uses **implicit control**. This is the simplest mode and is recommended for most use cases.

*   **Implicit Control (Simple):**
    *   You define a subscriber method that accepts the typed payload directly (e.g., `void handleOrder(Order order)`).
    *   The framework automatically **acks** the message if your method completes successfully.
    *   The framework automatically **naks** the message if your method throws an exception, triggering a redelivery.

```java
@NatsSubscriber(subject = "orders")
void handleOrder(Order order) {
    // Framework automatically acks on success, naks on exception.
    processOrder(order);
}
```

**Explicit control** gives you full responsibility for the message lifecycle. You enable it by changing your subscriber method's parameter to `NatsMessage<T>`.

*   **Explicit Control (Advanced):**
    *   You define a subscriber method that accepts a `NatsMessage<T>` wrapper (e.g., `void handleOrder(NatsMessage<Order> msg)`).
    *   The framework automatically **naks** messages that fail to deserialize or violate CloudEvent requirements (these errors occur before your method is invoked).
    *   For successfully deserialized messages, you **must** call `msg.ack()`, `msg.nak()`, or `msg.term()` to resolve the message.
    *   Unhandled exceptions in your subscriber method are logged but do NOT trigger automatic nak (unlike implicit mode).

```java
@NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "processor")
void handleOrder(NatsMessage<Order> msg) {
    // You decide when and how to ack/nak.
    Order order = msg.payload();
    try {
        processOrder(order);
        msg.ack();  // Explicit acknowledgment
    } catch (Exception e) {
        msg.nakWithDelay(Duration.ofSeconds(5));  // Request redelivery
    }
}
```

### When to Use Explicit Control

Use explicit control when you need to:
- Implement conditional acknowledgment based on business logic.
- Distinguish between transient errors (retry with `nak`) and permanent errors (ack and log).
- Implement custom retry strategies like exponential backoff.
- Route messages to a dead-letter queue after a certain number of retries.
- Access message metadata (like redelivery count) or headers.

---

## Example 1: Basic Acknowledgment

**Scenario**: Process an order and mark it as delivered only after all steps are complete.

```java
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderProcessor {

    @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
    public void handleOrder(NatsMessage<Order> msg) {
        Order order = msg.payload();

        System.out.println("Processing order: " + order.id());

        // Process the order (e.g., save to database, send email)
        saveOrderToDatabase(order);
        sendConfirmationEmail(order);

        // Acknowledge: the message is now marked as delivered.
        // It will NOT be redelivered, even if the application restarts.
        msg.ack();
    }

    private void saveOrderToDatabase(Order order) { /* ... */ }
    private void sendConfirmationEmail(Order order) { /* ... */ }
}
```

**Key Points**:
- `msg.payload()` returns the deserialized `Order` object.
- `msg.ack()` marks the message as successfully processed.

---

## Example 2: Error Handling with Conditional Ack/Nak

**Scenario**: Handle transient errors with a retry and permanent errors by logging and acknowledging.

```java
@ApplicationScoped
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "order-processor")
    public void handleOrder(NatsMessage<Order> msg) {
        Order order = msg.payload();

        try {
            validateOrder(order);
            saveOrder(order);

            // Success: acknowledge the message.
            msg.ack();
            log.info("Order processed: {}", order.id());

        } catch (NetworkException e) {
            // Transient error (e.g., network timeout, temporary DB unavailability).
            // Request redelivery after 5 seconds.
            msg.nakWithDelay(Duration.ofSeconds(5));
            log.warn("Transient error processing order {}: {}", order.id(), e.getMessage());

        } catch (ValidationException e) {
            // Permanent error (e.g., invalid order data, business rule violation).
            // Acknowledge to prevent retries, and log for manual review.
            msg.ack();
            log.error("Permanent error processing order {}: {}", order.id(), e.getMessage());
        }
    }
    
    private void validateOrder(Order order) throws ValidationException { /* ... */ }
    private void saveOrder(Order order) throws NetworkException { /* ... */ }
}
```

**Key Points**:
- `msg.nakWithDelay(Duration.ofSeconds(5))` requests redelivery after a 5-second delay.
- For unrecoverable errors, `msg.ack()` is used to remove the message from the queue and prevent endless retries.

---

## Example 3: Accessing Message Metadata and Headers

**Scenario**: Use CloudEvents headers and NATS metadata for tracing and implementing an exponential backoff retry strategy.

```java
@ApplicationScoped
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    @NatsSubscriber(subject = "events.*", stream = "EVENTS", consumer = "event-processor")
    public void handleEvent(NatsMessage<Event> msg) {
        Event event = msg.payload();

        // Extract CloudEvents headers
        String traceId = msg.headers().getFirst("traceparent");
        String eventType = msg.headers().getFirst("ce-type");

        // Access NATS metadata for retry logic
        long deliveryAttempt = msg.metadata().deliveredCount();

        log.info(
            "Event received: type={}, traceId={}, deliveryAttempt={}",
            eventType,
            traceId,
            deliveryAttempt
        );

        try {
            processEvent(event);
            msg.ack();
        } catch (Exception e) {
            // Implement exponential backoff for transient errors
            if (deliveryAttempt < 5) {
                long delaySeconds = (long) Math.pow(2, deliveryAttempt);
                msg.nakWithDelay(Duration.ofSeconds(delaySeconds));
                log.warn(
                    "Transient error. Retrying in {} seconds...",
                    delaySeconds
                );
            } else {
                // Max retries exceeded. Terminate the message to prevent further retries.
                msg.term();
                log.error("Max retries exceeded. Terminating message.", e);
            }
        }
    }

    private void processEvent(Event event) throws Exception {
        // Business logic...
    }
}
```

**Key Points**:
- `msg.headers().getFirst("ce-...")` accesses CloudEvents attributes.
- `msg.metadata().deliveredCount()` tracks the number of delivery attempts (1 for the first time).
- This metadata is crucial for building robust retry strategies like exponential backoff.

---

## Example 4: Terminating a Message

**Scenario**: A message is unrecoverable (e.g., business rule violation, unrecoverable state). Instead of retrying, you want to stop processing it immediately.

The `msg.term()` method tells the NATS server to stop redelivering the message, even if it hasn't reached its maximum delivery count. This is useful for preventing poison pills from endlessly cycling through your system.

**Note**: Deserialization errors and CloudEvent validation failures are automatically handled by the framework and will not invoke your subscriber method. Use `term()` only for business logic failures detected after successful deserialization.

```java
@ApplicationScoped
public class DataValidator {

    private static final Logger log = LoggerFactory.getLogger(DataValidator.class);

    @NatsSubscriber(stream = "DATA", consumer = "validator")
    public void handleData(NatsMessage<RawData> msg) {
        try {
            // msg.payload() returns successfully deserialized data
            // (deserialization errors are auto-nakked and never reach this method)
            RawData data = msg.payload();
            validate(data);

            // If valid, acknowledge and proceed.
            msg.ack();
            log.info("Data validated successfully: {}", data.id());

        } catch (ValidationException e) {
            // Unrecoverable error: the data failed business validation.
            // Terminate the message to prevent retries.
            msg.term();
            log.error("Invalid data received. Terminating message: {}", e.getMessage());
        }
    }

    private void validate(RawData data) throws ValidationException {
        if (data == null || data.id() == null || data.content() == null) {
            throw new ValidationException("Data is missing required fields.");
        }
        // ... more validation logic
    }
}
```

**When to Use `term()` vs. `ack()` for Errors**:

- Use `msg.term()` for messages that should **never** be processed again because they are fundamentally invalid.
- Use `msg.ack()` for business process failures where you accept the message but choose not to proceed (e.g., "Order declined"). Acknowledging it marks the event as successfully handled from a messaging perspective.

## API Reference: `NatsMessage<T>`

| Method | Description |
|---|---|
| `T payload()` | Returns the deserialized message payload. |
| `void ack()` | Acknowledges the message, marking it as successfully processed. |
| `void nak()` | Negatively acknowledges the message, requesting immediate redelivery. |
| `void nakWithDelay(Duration delay)` | Negatively acknowledges the message, requesting redelivery after the specified delay. |
| `void term()` | Terminates the message, preventing redelivery. This is useful for certain consumer configurations. |
| `Headers headers()` | Returns the message headers, including CloudEvents attributes. |
| `String subject()` | Returns the NATS subject the message was received on. |
| `MessageMetadata metadata()` | Returns NATS JetStream metadata, such as sequence numbers and redelivery count. |

---

## Troubleshooting

### `JetStreamApiException`: `nak received for a message that is not outstanding`

**Cause**: Your NATS consumer is not configured with the correct acknowledgment policy. Explicit `nak` requires `AckPolicy=explicit`.

**Fix**: Ensure your durable consumer is created with the explicit acknowledgment policy:
```bash
nats consumer add YOUR_STREAM YOUR_CONSUMER --ack explicit
```

### Message is redelivered after `ack()`

**Cause**: This can happen if the `ack()` call itself fails (e.g., due to a network issue) or if there's a misconfiguration in the consumer.

**Check**:
1.  Verify your consumer configuration with `nats consumer info YOUR_STREAM YOUR_CONSUMER`.
2.  Ensure the stream and consumer names in your `@NatsSubscriber` annotation are correct.
3.  Wrap your `ack()` call in a `try-catch` block to handle potential network errors during acknowledgment.
