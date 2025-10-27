# Quickstart: Durable Consumers for @NatsSubscriber

**Feature**: `008-durable-nats-consumers` | **Date**: 2025-10-27

## Overview

This guide shows how to use durable consumers with the Quarkus EasyNATS extension to achieve message processing that survives application restarts. Durable consumers persist unprocessed messages on the NATS server, so your application can resume from where it left off.

---

## Step 1: Pre-configure Durable Consumer on NATS

Before using a durable consumer in your Quarkus application, you must create it on your NATS JetStream server. The extension does NOT create consumers—they must be pre-configured by your NATS operator/admin.

### Using NATS CLI

```bash
# 1. Create a stream (if not already created)
nats stream add my-stream --subjects "my-subject.>"

# 2. Create a durable consumer on the stream
nats consumer add my-stream my-durable-consumer \
  --filter="my-subject.>" \
  --ack=explicit \
  --flow-control=true
```

### Verify Consumer Creation

```bash
# List consumers on stream
nats consumer ls my-stream

# View consumer details
nats consumer info my-stream my-durable-consumer
```

---

## Step 2: Configure Quarkus Application

Ensure your `application.properties` has NATS connection settings:

```properties
nats.servers=nats://localhost:4222
nats.username=admin
nats.password=password
nats.ssl-enabled=false
```

---

## Step 3: Use @NatsSubscriber with Durable Consumer

Annotate your subscriber method with the durable consumer's stream and name:

```java
import jakarta.enterprise.context.ApplicationScoped;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;

@ApplicationScoped
public class DurableConsumerListener {

    /**
     * Ephemeral consumer: message NOT persisted across restarts
     * (existing pattern from feature 004)
     */
    @NatsSubscriber(subject = "logs.>")
    void handleLogs(String logMessage) {
        System.out.println("Log: " + logMessage);
    }

    /**
     * Durable consumer: message persisted and redelivered on restart
     * (new pattern in feature 001)
     */
    @NatsSubscriber(stream = "my-stream", consumer = "my-durable-consumer")
    void handleMessage(String message) {
        System.out.println("Received: " + message);
        // If method returns normally: message is ack'd
        // If method throws exception: message is nak'd (redelivered later)
    }
}
```

### What Happens at Startup

1. Application starts → EasyNATS extension discovers @NatsSubscriber annotations
2. **Build-time**: Validates annotation properties
   - ❌ Fails if both `subject` and `stream/consumer` are specified
   - ❌ Fails if only one of `stream` or `consumer` is specified
3. **Startup**: For durable consumers, verifies consumer exists on NATS server
   - ✅ Success: Binds to consumer and starts receiving messages
   - ❌ Failure: Application startup fails with error message

---

## Step 4: Test Message Durability

### Test Scenario: Message Survival Across Restarts

1. **Setup**: Stream and consumer created on NATS (see Step 1)

2. **Publish a test message**:
   ```bash
   nats pub my-subject.test "Hello from NATS"
   ```

3. **Start application**:
   ```bash
   ./mvnw quarkus:dev
   ```
   - Output: `Received: Hello from NATS`

4. **Stop application** (Ctrl+C)
   - Message was processed and ack'd
   - Consumer queue is empty

5. **Publish another message** (while app is stopped):
   ```bash
   nats pub my-subject.test "Message while app is down"
   ```

6. **Restart application**:
   ```bash
   ./mvnw quarkus:dev
   ```
   - Output: `Received: Message while app is down`
   - **Key Point**: Message was not lost during downtime (durability!)

### Test Scenario: Message Redelivery on Error

1. **Modify subscriber to throw exception**:
   ```java
   @NatsSubscriber(stream = "my-stream", consumer = "my-durable-consumer")
   void handleMessage(String message) {
       throw new RuntimeException("Processing failed");
   }
   ```

2. **Start application**:
   ```bash
   ./mvnw quarkus:dev
   ```

3. **Publish message**:
   ```bash
   nats pub my-subject.test "Test message"
   ```

4. **Observe**:
   - Application logs error
   - Message is nak'd (not acknowledged)
   - NATS will redeliver the message
   - Process repeats until you fix the bug

5. **Fix the bug** and restart application
   - Message is redelivered and processed successfully

---

## Step 5: Using Typed Messages (Optional)

Durable consumers work seamlessly with typed messages (from feature 007-typed-serialization):

```java
import jakarta.enterprise.context.ApplicationScoped;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;

public record Order(String id, BigDecimal amount) { }

@ApplicationScoped
public class OrderProcessor {

    @NatsSubscriber(stream = "orders-stream", consumer = "order-processor-v1")
    void processOrder(Order order) {
        // Order is automatically deserialized from JSON
        System.out.println("Processing order: " + order.id() + ", amount: " + order.amount());
    }
}
```

**Message Format**: JSON (sent via publisher, consumed as Order object)
```json
{"id":"ORD-123","amount":99.99}
```

---

## Troubleshooting

### Error: "Failed to verify durable consumer: Stream 'X' does not contain consumer 'Y'"

**Cause**: Consumer doesn't exist or name is wrong.

**Solution**:
1. Verify consumer exists: `nats consumer ls my-stream`
2. Verify names match annotation exactly (case-sensitive)
3. If missing, create it: `nats consumer add my-stream my-durable-consumer ...`

### Error: Build fails with "Cannot specify both subject and stream/consumer properties"

**Cause**: Annotation specifies both ephemeral and durable properties.

**Solution**:
```java
// ❌ Wrong: Both subject and stream/consumer
@NatsSubscriber(subject = "logs", stream = "my-stream", consumer = "my-consumer")

// ✅ Correct: Either subject (ephemeral) OR stream+consumer (durable)
@NatsSubscriber(subject = "logs")                    // ephemeral
@NatsSubscriber(stream = "my-stream", consumer = "my-consumer")  // durable
```

### Messages Not Being Processed

**Possible Causes**:
1. **Consumer filter doesn't match message subject**:
   - Consumer created with filter: `orders.>` (matches `orders.new`, `orders.update`, etc.)
   - Message published to: `inventory.stock` (no match)
   - **Solution**: Verify message subject matches consumer filter

2. **NATS connection settings wrong**:
   - Check `nats.servers`, `nats.username`, `nats.password` in `application.properties`
   - **Solution**: Verify credentials and server address

3. **Application didn't start due to consumer missing**:
   - **Solution**: Check application logs for startup error about consumer verification

---

## Key Differences: Ephemeral vs. Durable

| Feature | Ephemeral (`@NatsSubscriber(subject = "...")`) | Durable (`@NatsSubscriber(stream = "...", consumer = "...")`) |
|---------|------|--------|
| Consumer Created | Auto-created by JNATS on startup | Pre-configured on NATS server |
| Message Persistence | NO—lost on app restart | YES—persisted across restarts |
| Consumer Cleanup | Auto-deleted when app stops | Remains on server; can be reused |
| Use Case | Log aggregation, event streams | Order processing, critical workflows |
| Configuration | Just a subject | Stream + consumer name (pre-created) |

---

## Next Steps

- **Advanced Usage**: See `contracts.md` for detailed API specifications
- **Data Model**: See `data-model.md` for entity definitions and validation rules
- **Implementation**: See `plan.md` for technical design and build-time validation details

---

**Summary**: Create your durable consumer on NATS first, then annotate your subscriber with `@NatsSubscriber(stream="...", consumer="...")`. The extension handles the rest—message durability, automatic ack/nak, and restart recovery are automatic.
