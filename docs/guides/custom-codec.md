# Custom Payload Codec Guide - Quarkus EasyNATS

This guide explains how to implement and use a custom payload codec to control serialization and deserialization in Quarkus EasyNATS.

## Overview

By default, Quarkus EasyNATS uses **Jackson** to serialize and deserialize message payloads as JSON. For most use cases, this is ideal. However, you may need a custom codec when:

- **Different data format**: You need to use Protobuf, MessagePack, or another binary format instead of JSON
- **Custom validation**: You need to validate payloads during deserialization (e.g., verify certain fields meet business rules)
- **Protocol translation**: You need to translate between different protocol versions or formats
- **Legacy system integration**: You need to handle a specific encoding used by legacy systems
- **Compression**: You want to compress/decompress payloads transparently

---

## How It Works

### Default Behavior (Jackson)

```
Publisher          Codec          NATS Message (CloudEvent)
OrderData -------> encode() -----> data: JSON bytes
                                    datacontenttype: application/json

NATS Message       Codec          Subscriber
CloudEvent ------> decode() -----> OrderData
```

### Custom Codec Pattern

When you provide a custom `Codec` bean, it overrides the default Jackson-based codec:

```
Publisher                  Custom Codec              NATS Message (CloudEvent)
OrderData ------------> yourCodec.encode() ------> data: custom format bytes
                                                     datacontenttype: your/format

NATS Message               Custom Codec             Subscriber
CloudEvent -----------> yourCodec.decode() ------> OrderData
```

---

## Implementing a Custom Codec

### Step 1: Implement the Codec Interface

Create a class that implements `org.mjelle.quarkus.easynats.codec.Codec`:

```java
package com.example.nats;

import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import org.mjelle.quarkus.easynats.codec.SerializationException;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProtobufCodec implements Codec {

    @Override
    public String getContentType() {
        // Return the MIME type for CloudEvent datacontenttype header
        return "application/protobuf";
    }

    @Override
    public byte[] encode(Object object) throws SerializationException {
        if (object == null) {
            throw new SerializationException("Cannot encode null object");
        }

        try {
            // Use your serialization library (e.g., protobuf)
            return MyProtobufLibrary.serialize(object);
        } catch (Exception e) {
            throw new SerializationException(
                "Failed to serialize to Protobuf: " + e.getMessage(), e
            );
        }
    }

    @Override
    public Object decode(byte[] data, Class<?> type, String ceType)
            throws DeserializationException {

        if (data == null || data.length == 0) {
            throw new DeserializationException("Cannot decode null or empty data");
        }

        if (type == null) {
            throw new DeserializationException("Target type cannot be null");
        }

        try {
            // Use your deserialization library
            return MyProtobufLibrary.deserialize(data, type);
        } catch (Exception e) {
            throw new DeserializationException(
                "Failed to deserialize from Protobuf to " + type.getSimpleName() + ": " + e.getMessage(), e
            );
        }
    }
}
```

### Step 2: Register as a CDI Bean

The codec **must be registered as an `@ApplicationScoped` CDI bean**. Quarkus EasyNATS will automatically discover and use it via CDI.

```java
@ApplicationScoped  // ← Required: Makes it discoverable by EasyNATS
public class ProtobufCodec implements Codec {
    // ... implementation ...
}
```

That's it! The codec will override the default Jackson codec for all publishers and subscribers.

### Step 3: Use in Publishers and Subscribers

No changes needed! Just use your message types as normal:

```java
// Publishing
@Dependent
public class OrderService {
    private final NatsPublisher<OrderData> publisher;

    public OrderService(
        @NatsSubject("orders") NatsPublisher<OrderData> publisher
    ) {
        this.publisher = publisher;
    }

    public void publishOrder(OrderData order) throws Exception {
        // Your custom codec automatically encodes the order to Protobuf
        publisher.publish(order);
    }
}

// Subscribing
@Singleton
public class OrderListener {
    @NatsSubscriber("orders")
    public void handleOrder(OrderData order) {
        // Your custom codec automatically decoded Protobuf to OrderData
        System.out.println("Received: " + order);
    }
}
```

---

## Error Handling & Validation

### Throwing Errors During Encode

If encoding fails, throw `SerializationException`:

```java
@Override
public byte[] encode(Object object) throws SerializationException {
    try {
        return MyProtobufLibrary.serialize(object);
    } catch (MyLibraryException e) {
        throw new SerializationException("Encoding failed: " + e.getMessage(), e);
    }
}
```

**Result**: The publish operation fails with `SerializationException`. The message is not sent to NATS.

### Throwing Errors During Decode (Implicit Mode)

If validation or decoding fails, throw `DeserializationException`:

```java
@Override
public Object decode(byte[] data, Class<?> type, String ceType)
        throws DeserializationException {

    Object result = MyProtobufLibrary.deserialize(data, type);

    // Custom validation
    if (result instanceof OrderData) {
        OrderData order = (OrderData) result;
        if (order.getAmount() < 0) {
            throw new DeserializationException("Order amount cannot be negative");
        }
    }

    return result;
}
```

**Result (Implicit Mode - typed subscriber method)**:
1. The framework catches `DeserializationException`
2. Logs the error
3. **Does NOT invoke** your subscriber method
4. **Negatively acknowledges (NACKs)** the message → NATS retries it

This prevents invalid messages from being processed.

### Error Handling with Explicit Mode

If you use `NatsMessage<T>` for explicit acknowledgment:

```java
@Singleton
public class OrderListener {
    @NatsSubscriber("orders")
    public void handleOrder(NatsMessage<OrderData> message) {
        try {
            OrderData order = message.getPayload();
            // Process order...
            message.ack();  // Acknowledge success
        } catch (InvalidOrderException e) {
            // You decide: retry or discard?
            message.nak();  // Retry (requeue)
            // Or: message.nakWithDelay(Duration.ofSeconds(5));
        }
    }
}
```

**Note**: In explicit mode, deserialization errors are NOT caught by the framework. Your method receives the `NatsMessage` and can handle errors as needed.

---

## Real-World Example: Adding Compression

This example shows a codec that compresses payloads using GZIP:

```java
package com.example.nats;

import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import org.mjelle.quarkus.easynats.codec.SerializationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@ApplicationScoped
public class CompressedJsonCodec implements Codec {

    private final ObjectMapper objectMapper;

    @Inject
    public CompressedJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getContentType() {
        // Use a custom content type to indicate compression
        return "application/json+gzip";
    }

    @Override
    public byte[] encode(Object object) throws SerializationException {
        if (object == null) {
            throw new SerializationException("Cannot encode null object");
        }

        try {
            // 1. Serialize to JSON with Jackson
            byte[] jsonBytes = objectMapper.writeValueAsBytes(object);

            // 2. Compress with GZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(jsonBytes);
            }

            return baos.toByteArray();
        } catch (Exception e) {
            throw new SerializationException(
                "Failed to encode and compress: " + e.getMessage(), e
            );
        }
    }

    @Override
    public Object decode(byte[] data, Class<?> type, String ceType)
            throws DeserializationException {

        if (data == null || data.length == 0) {
            throw new DeserializationException("Cannot decode null or empty data");
        }

        if (type == null) {
            throw new DeserializationException("Target type cannot be null");
        }

        try {
            // 1. Decompress with GZIP
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
                byte[] jsonBytes = gzip.readAllBytes();

                // 2. Deserialize from JSON with Jackson
                return objectMapper.readValue(jsonBytes, type);
            }
        } catch (Exception e) {
            throw new DeserializationException(
                "Failed to decompress and decode: " + e.getMessage(), e
            );
        }
    }
}
```

**Usage**: Just annotate with `@ApplicationScoped` and it works automatically with all publishers and subscribers!

```java
publisher.publish(largeOrder);  // Automatically compressed
listener.handleOrder(order);     // Automatically decompressed
```

---

## Real-World Example: Protocol Validation

This example validates messages during deserialization:

```java
package com.example.nats;

import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import org.mjelle.quarkus.easynats.codec.SerializationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ValidatingCodec implements Codec {

    private final ObjectMapper objectMapper;

    @Inject
    public ValidatingCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public byte[] encode(Object object) throws SerializationException {
        if (object == null) {
            throw new SerializationException("Cannot encode null object");
        }

        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (Exception e) {
            throw new SerializationException(
                "Serialization failed: " + e.getMessage(), e
            );
        }
    }

    @Override
    public Object decode(byte[] data, Class<?> type, String ceType)
            throws DeserializationException {

        if (data == null || data.length == 0) {
            throw new DeserializationException("Empty payload");
        }

        try {
            Object object = objectMapper.readValue(data, type);

            // Custom validation
            validateObject(object);

            return object;
        } catch (DeserializationException e) {
            // Re-throw validation errors
            throw e;
        } catch (Exception e) {
            throw new DeserializationException(
                "Failed to deserialize: " + e.getMessage(), e
            );
        }
    }

    private void validateObject(Object object) throws DeserializationException {
        // Example: Validate OrderData business rules
        if (object instanceof OrderData) {
            OrderData order = (OrderData) object;

            if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
                throw new DeserializationException("Order ID is required");
            }

            if (order.getAmount() == null || order.getAmount().signum() <= 0) {
                throw new DeserializationException("Order amount must be positive");
            }
        }
    }
}
```

When a message fails validation, the subscriber is NOT called and the message is NACKed (retried).

---

## Best Practice: Handling Repeated Failures with DLQ

When a codec validation fails and the message is NACKed, NATS will retry the message indefinitely by default. To prevent infinite retries while preserving messages, use the NATS consumer's `max_deliver` setting with a dead-letter queue (DLQ).

### Why the Framework NACKs Instead of TERMing

Quarkus EasyNATS does **not** silently discard (TERM) messages when codec validation fails. Here's why:

- **No silent data loss**: We won't lose your messages on your behalf due to a codec or schema issue
- **Your responsibility**: Only you can decide if a message is truly unrecoverable
- **Visibility**: DLQ gives you a record of which messages failed and why
- **Recovery**: If you fix a codec bug, you can manually reprocess failed messages from the DLQ
- **Auditability**: Complete audit trail for compliance and debugging

This philosophy ensures your application **never loses data** due to framework decisions, while NACKing preserves messages for investigation and recovery.

### Setup: Consumer with Max Deliver & DLQ

Configure your NATS JetStream consumer to give up after N failed attempts and automatically send to a DLQ subject.

**Complete Setup Script (Recommended)**

Create a `setup-dlq.sh` script to set up both main and DLQ streams with a consumer:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Create main ORDERS stream
nats stream add ORDERS --subjects "orders.*" --storage file --retention limits \
  --discard old --max-msgs=-1 --max-bytes=-1 --max-age=0 --dupe-window=2m \
  --replicas=1 --defaults <<EOF
y
EOF

# Create DLQ stream for failed messages
nats stream add ORDERS_DLQ --subjects "orders.dlq" --storage file --retention limits \
  --discard old --max-msgs=-1 --max-bytes=-1 --max-age=0 --dupe-window=2m \
  --replicas=1 --defaults <<EOF
y
EOF

# Create consumer with max_deliver=5 and DLQ republish
cat > consumer.json <<JSON
{
  "ack_policy": "explicit",
  "max_deliver": 5,
  "republish": {
    "source": true,
    "destination": "orders.dlq"
  }
}
JSON

nats consumer add ORDERS order-worker --config=consumer.json <<EOF
y
EOF

echo "✅ Streams ORDERS, ORDERS_DLQ and consumer order-worker created."
```

Run it:
```bash
chmod +x setup-dlq.sh
./setup-dlq.sh
```

### How It Works

```
Message with invalid codec
         ↓
   Codec validation fails
         ↓
   Framework NACKs (redelivery attempt 1, backoff 1s)
         ↓
   Codec validation fails again
         ↓
   Framework NACKs (redelivery attempt 2, backoff 2s)
         ↓
   ... (attempts 3, 4, 5 with increasing backoff)
         ↓
   After 5 max_deliver attempts, NATS automatically sends to DLQ
         ↓
   Message appears in orders.dlq subject (NATS handles republish)
         ↓
   DLQ consumer can investigate and log for ops
```

### DLQ Consumer for Monitoring & Investigation

Subscribe to the DLQ to monitor, investigate, and log problematic messages:

```java
@Singleton
public class OrderDLQListener {

    private static final Logger LOGGER = Logger.getLogger(OrderDLQListener.class);

    @NatsSubscriber("orders.dlq")
    public void handleDLQMessage(NatsMessage<String> message) {
        try {
            // Log the failed message for investigation
            String rawPayload = message.getPayload();
            String redeliveryCount = message.getHeaders()
                .getFirst("Nats-Delivery-Count");

            LOGGER.error(
                "Message moved to DLQ after {} delivery attempts. " +
                "Raw payload: {} | Cause: Codec validation failed",
                redeliveryCount, rawPayload
            );

            // Store for manual investigation & repair
            storeDLQMessage(rawPayload, redeliveryCount);

            // Alert ops team if needed
            alertOpsTeam("Codec validation failure in orders stream", rawPayload);

            message.ack();
        } catch (Exception e) {
            LOGGER.error("Error handling DLQ message", e);
            message.nak();
        }
    }

    private void storeDLQMessage(String payload, String redeliveryCount) {
        // Store in database for investigation and manual repair
    }

    private void alertOpsTeam(String reason, String payload) {
        // Send alert to ops dashboard or Slack
    }
}
```

### Configuration Recommendations

**For development/testing:**
```
max_deliver=3      # Fail fast
backoff_step=100ms # Quick feedback
```

**For production:**
```
max_deliver=5-10        # Allow reasonable retry attempts
backoff_step=1-5s       # Exponential backoff to reduce load
dlq_subject=<topic>.dlq # Dedicated DLQ for investigation
```

### Monitoring the DLQ

- **Count messages in DLQ**: Indicates codec or data quality issues
- **Alert on DLQ growth**: Set up monitoring to alert when DLQ receives messages
- **Regular review**: Investigate DLQ messages to fix root causes (codec bug, malformed data, etc.)

---

## Thread Safety

Your codec **must be thread-safe**. It will be called concurrently by multiple publishers and subscribers.

### Safe Design (✅)

```java
@ApplicationScoped
public class MyCodec implements Codec {

    // Thread-safe: Jackson ObjectMapper is thread-safe
    private final ObjectMapper objectMapper;

    @Inject
    public MyCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // No instance state → Safe for concurrent access
    @Override
    public byte[] encode(Object object) throws SerializationException {
        // Just use objectMapper - thread-safe
    }
}
```

### Unsafe Design (❌)

```java
@ApplicationScoped
public class BadCodec implements Codec {

    // NOT thread-safe: SimpleDateFormat is not thread-safe
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public byte[] encode(Object object) throws SerializationException {
        // ❌ Race condition if multiple threads call this simultaneously
        return dateFormat.format(new Date()).getBytes();
    }
}
```

**Solutions for thread-unsafe components**:
- Use thread-local storage: `ThreadLocal<SimpleDateFormat>`
- Create new instance each time: `new SimpleDateFormat(...)`
- Use thread-safe alternatives: `DateTimeFormatter` (Java 8+)
- Lock access: `synchronized` (performance impact)

---

## Testing Your Codec

### Unit Test Example

```java
class MyCodecTest {

    private MyCodec codec;

    @BeforeEach
    void setUp() {
        codec = new MyCodec(new ObjectMapper());
    }

    @Test
    void testEncode() throws SerializationException {
        OrderData order = new OrderData("ORDER-123", 100.0);
        byte[] encoded = codec.encode(order);
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void testDecode() throws DeserializationException {
        OrderData order = new OrderData("ORDER-123", 100.0);
        byte[] data = codec.encode(order);

        OrderData decoded = (OrderData) codec.decode(data, OrderData.class, null);
        assertThat(decoded).isEqualTo(order);
    }

    @Test
    void testValidationError() {
        byte[] invalidData = "{\"amount\": -100}".getBytes();

        assertThatThrownBy(() -> {
            codec.decode(invalidData, OrderData.class, null);
        }).isInstanceOf(DeserializationException.class);
    }
}
```

### Integration Test Example

```java
@QuarkusTest
class CustomCodecIT {

    @Test
    void testCodecUsedInPublishSubscribe() throws Exception {
        OrderData order = new OrderData("ORDER-456", 250.0);

        // Publish (uses codec.encode())
        given()
            .contentType(ContentType.JSON)
            .body(order)
            .when()
            .post("/publish/order")
            .then()
            .statusCode(204);

        // Subscribe (uses codec.decode())
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                OrderData result = given()
                    .when()
                    .get("/subscribe/last-order")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(OrderData.class);

                assertThat(result).isEqualTo(order);
            });
    }
}
```

---

## Troubleshooting

### Issue: Codec Not Being Used

**Symptom**: Messages are still serialized as JSON even though you implemented a custom codec.

**Causes**:
1. Codec is not annotated with `@ApplicationScoped`
2. Codec doesn't implement `Codec` interface
3. Codec package is not scanned by Quarkus (check `quarkus.package.excluded-artifacts`)

**Solution**:
```java
@ApplicationScoped  // ← Required!
public class MyCodec implements Codec {
    // ...
}
```

### Issue: Codec Decode Error Breaks Subscriber

**Symptom**: Your subscriber method is called even though decode() threw an exception.

**Causes**:
1. You're using explicit mode (`NatsMessage<T>`) - the framework doesn't auto-NAK
2. An exception was thrown after codec.decode() returned successfully

**Solution**:
- If using explicit mode, handle errors manually:
  ```java
  @NatsSubscriber("orders")
  public void handle(NatsMessage<OrderData> msg) {
      try {
          OrderData order = msg.getPayload();
          // process...
          msg.ack();
      } catch (Exception e) {
          msg.nak();  // ← You control this
      }
  }
  ```

### Issue: Type Information Not Available in Decode

**Symptom**: You need to know which type is being decoded, but the `ceType` header is always null.

**Cause**: The CloudEvent `ce-type` header depends on the publisher setting it. The default codec doesn't set it automatically.

**Solution**: Set the header explicitly in your publisher, or use the `type` parameter:
```java
@Override
public Object decode(byte[] data, Class<?> type, String ceType)
        throws DeserializationException {
    // The 'type' parameter tells you what to deserialize to
    String typeName = type.getSimpleName();  // "OrderData", "UserProfile", etc.
    // ...
}
```

---

## See Also

- **[Explicit Ack/Nak Guide](./explicit-acknowledgment.md)** - Manual message handling
- **[Configuration Guide](../CONFIGURATION.md)** - Setting up NATS connection
- **[Jackson Annotations Guide](./jackson-annotations.md)** - Customizing Jackson serialization

