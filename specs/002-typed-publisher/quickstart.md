# Quickstart: Typed NatsPublisher with CloudEvents Support

**Feature**: MVP 002 Typed NatsPublisher | **Date**: 2025-10-26 | **Phase**: Design Artifacts (Phase 1)

This guide demonstrates how to use the MVP 002 Typed NatsPublisher extension with JSON payload serialization and CloudEvents support.

---

## Installation

Add the Quarkus EasyNATS extension to your `pom.xml`:

```xml
<dependency>
    <groupId>org.mjelle.quarkus</groupId>
    <artifactId>quarkus-easy-nats</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Configuration

Configure NATS connection in `application.properties`:

```properties
# Required: NATS broker address(es)
nats.servers=nats://localhost:4222

# Required: Authentication username
nats.username=admin

# Required: Authentication password
nats.password=secret

# Optional: Enable SSL/TLS
nats.ssl-enabled=false
```

Or in `application.yaml`:

```yaml
nats:
  servers: nats://localhost:4222
  username: admin
  password: secret
  ssl-enabled: false
```

---

## Basic Usage: Publishing Primitive Types

### Example 1: Publish a String

```java
import org.mjelle.quarkus.easynats.NatsPublisher;
import jakarta.inject.Singleton;

@Singleton
public class StringPublisher {
    private final NatsPublisher<String> publisher;

    StringPublisher(NatsConnectionManager connectionManager) {
        this.publisher = new NatsPublisher<>(connectionManager);
    }

    public void publishMessage(String message) {
        publisher.publish(message);
        System.out.println("Published: " + message);
    }
}
```

**How it works**:
1. `NatsPublisher<String>` is a type-safe publisher for String payloads
2. `publisher.publish("hello world")` encodes the string to UTF-8 bytes
3. The message is sent to NATS subject "test" without JSON serialization overhead
4. Uses native encoder (Priority 1 in resolution order)

### Example 2: Publish an Integer

```java
@Singleton
public class IntegerPublisher {
    private final NatsPublisher<Integer> publisher;

    IntegerPublisher(NatsConnectionManager connectionManager) {
        this.publisher = new NatsPublisher<>(connectionManager);
    }

    public void publishNumber(int number) {
        publisher.publish(number);
    }
}
```

**How it works**:
- `publisher.publish(42)` encodes as "42"
- Direct string conversion; no Jackson overhead
- Supported primitives: `int`, `long`, `byte`, `short`, `double`, `float`, `boolean`, `char`

### Example 2b: Publish Byte Arrays (Base64 Encoding)

```java
@Singleton
public class BytePublisher {
    private final NatsPublisher<byte[]> publisher;

    BytePublisher(NatsConnectionManager connectionManager) {
        this.publisher = new NatsPublisher<>(connectionManager);
    }

    public void publishBinaryData(byte[] data) {
        publisher.publish(data);  // Automatically base64-encoded
    }
}
```

**How it works**:
- `publisher.publish(new byte[]{1,2,3})` encodes as base64 string
- **IMPORTANT**: Byte arrays are NEVER published as raw binary; always base64-encoded for NATS compatibility
- This ensures message payloads remain text-based and compatible with NATS headers
- Supported byte types: `byte`, `Byte`, `byte[]`
- Use base64 decoding on subscriber side to recover original bytes

---

## Intermediate Usage: Publishing Domain Objects (Complex Types)

### Example 3: Publish a Custom Domain Object

First, define your domain class with `@RegisterForReflection` (required for GraalVM native image support):

```java
import io.quarkus.runtime.annotations.RegisterForReflection;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * IMPORTANT: @RegisterForReflection is REQUIRED for GraalVM native image compilation.
 * Without it, Jackson reflection will fail in native builds.
 */
@RegisterForReflection
public class Order {
    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("customerEmail")
    private String customerEmail;

    @JsonProperty("amount")
    private double amount;

    // Zero-arg constructor (required by Jackson)
    public Order() {}

    // Field constructor
    public Order(String orderId, String customerEmail, double amount) {
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.amount = amount;
    }

    // Getters and setters (required by Jackson)
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
```

Now publish it:

```java
import org.mjelle.quarkus.easynats.NatsPublisher;
import jakarta.inject.Singleton;

@Singleton
public class OrderPublisher {
    private final NatsPublisher<Order> publisher;

    OrderPublisher(NatsConnectionManager connectionManager) {
        this.publisher = new NatsPublisher<>(connectionManager);
    }

    public void publishOrder(Order order) {
        publisher.publish(order);
        System.out.println("Order published: " + order.getOrderId());
    }
}
```

**Usage**:

```java
Order order = new Order("ORD-123", "alice@example.com", 99.99);
orderPublisher.publishOrder(order);

// Published to NATS as JSON:
// {"orderId":"ORD-123","customerEmail":"alice@example.com","amount":99.99}
```

**How it works**:
1. `@RegisterForReflection` enables Jackson reflection for GraalVM native image
2. `publisher.publish(order)` serializes to JSON using Jackson
3. Zero-arg constructor and getters/setters are required by Jackson
4. Uses Jackson encoder (Priority 3 in resolution order)
5. On serialization failure, throws `SerializationException` with user-friendly message

### Important: @RegisterForReflection for Native Image

If you deploy your Quarkus application as a native image (GraalVM), all complex types MUST be annotated with `@RegisterForReflection`:

```java
// ✅ CORRECT - works in both JVM and native image modes
@RegisterForReflection
public class UserCreatedEvent {
    private String userId;
    private String email;
    // ...
}
```

**Why is this needed?**
- Java generics are erased at compile time; type information is not available at runtime
- GraalVM native image compilation requires pre-computed reflection metadata
- The `@RegisterForReflection` annotation tells the Quarkus build system to generate this metadata
- Without it, Jackson reflection calls fail in native image with `IllegalAccessException`

**Testing your domain object**:

```java
// Before deploying, verify your class is correctly annotated
@RegisterForReflection
public class MyEvent {
    public MyEvent() {}  // ✅ Zero-arg constructor required

    private String field1;

    public String getField1() { return field1; }  // ✅ Getter required
    public void setField1(String field1) { this.field1 = field1; }  // ✅ Setter required
}
```

---

## Advanced Usage: CloudEvents Support

### Example 4: Publish with CloudEvents Metadata

CloudEvents is a CNCF standard for describing events. MVP 002 supports CloudEvents spec 1.0 with automatic header generation.

```java
@Singleton
public class CloudEventPublisher {
    private final NatsPublisher<Order> publisher;

    CloudEventPublisher(NatsConnectionManager connectionManager) {
        this.publisher = new NatsPublisher<>(connectionManager);
    }

    public void publishOrderEvent(Order order) {
        // Publish with explicit CloudEvents metadata
        publisher.publishCloudEvent(
            order,
            "com.example.OrderPlaced",  // ceType: event type
            "/order-service"             // ceSource: event source
        );
    }

    public void publishWithAutoGeneratedMetadata(Order order) {
        // Publish with auto-generated CloudEvents metadata
        publisher.publishCloudEvent(
            order,
            null,  // ceType: auto-generated from class name (org.example.Order)
            null   // ceSource: auto-generated from hostname
        );
    }
}
```

**Usage**:

```java
Order order = new Order("ORD-456", "bob@example.com", 149.99);

// Explicitly set CloudEvents metadata
publisher.publishOrderEvent(order);

// Or auto-generate metadata
publisher.publishWithAutoGeneratedMetadata(order);
```

**Published Message Structure**:

The message sent to NATS will have:

```
NATS Message (subject: "test")
├── Headers (CloudEvents metadata):
│   ├── ce-specversion: 1.0
│   ├── ce-type: com.example.OrderPlaced (or auto-generated)
│   ├── ce-source: /order-service (or auto-generated from hostname)
│   ├── ce-id: 550e8400-e29b-41d4-a716-446655440000 (auto-generated UUID)
│   ├── ce-time: 2025-10-26T14:30:45.123456Z (auto-generated ISO 8601)
│   └── ce-datacontenttype: application/json
│
└── Body (event payload as JSON):
    {"orderId":"ORD-456","customerEmail":"bob@example.com","amount":149.99}
```

### Example 5: CloudEvents with Auto-Generated Metadata

If you don't specify `ceType` and `ceSource`, they are auto-generated:

```java
@RegisterForReflection
public class UserCreatedEvent {
    private String userId;
    private String email;

    // constructors, getters, setters...
}

@Singleton
public class UserPublisher {
    private final NatsPublisher<UserCreatedEvent> publisher;

    UserPublisher(NatsConnectionManager connectionManager) {
        this.publisher = new NatsPublisher<>(connectionManager);
    }

    public void publishUserCreated(UserCreatedEvent event) {
        // CloudEvents metadata auto-generated
        publisher.publishCloudEvent(event, null, null);

        // Results in:
        // ce-type: org.example.UserCreatedEvent (fully-qualified class name)
        // ce-source: myapp-service (from hostname or app name)
        // ce-id: <uuid>
        // ce-time: <iso-8601 timestamp>
    }
}
```

---

## Error Handling

### Handling Null Objects

Attempting to publish `null` throws an `IllegalArgumentException`:

```java
try {
    Order order = null;
    publisher.publish(order);  // ❌ Throws IllegalArgumentException
} catch (IllegalArgumentException e) {
    System.err.println("Error: " + e.getMessage());  // "Cannot publish null object"
}
```

### Handling Serialization Errors

If an object cannot be serialized to JSON, a `SerializationException` is thrown:

```java
@RegisterForReflection
public class BadEvent {
    private String field;

    // ❌ WRONG: No zero-arg constructor
    // This will cause Jackson serialization to fail
}

try {
    publisher.publish(new BadEvent());
} catch (SerializationException e) {
    System.err.println("Serialization failed: " + e.getMessage());
    // "Failed to serialize BadEvent: missing zero-arg constructor"
}
```

**To fix**:
1. Ensure your domain class has a zero-arg constructor
2. Ensure all fields have public getters and setters (or use Jackson annotations)
3. Annotate with `@RegisterForReflection` for native image support

---

## Encoder/Decoder Resolution Order

The extension uses a priority-based resolution strategy for encoding:

```
┌─────────────────────────────────┐
│ publish(T payload)              │
└─────────────────────────────────┘
             ↓
┌─────────────────────────────────┐
│ Check: Is T a primitive type?   │
│ (int, long, String, etc.)       │
├─────────────────────────────────┤
│ YES → Encode directly (UTF-8)   │
│ NO  → Check next priority       │
└─────────────────────────────────┘
             ↓
┌─────────────────────────────────┐
│ Check: Is T a byte type?        │
│ (byte, Byte, byte[])            │
├─────────────────────────────────┤
│ YES → Encode as Base64 (never   │
│       raw binary)               │
│ NO  → Check next priority       │
└─────────────────────────────────┘
             ↓
┌─────────────────────────────────┐
│ Check: Is T an array type?      │
│ (int[], String[], etc.)         │
├─────────────────────────────────┤
│ YES → Encode array (UTF-8       │
│       space/comma-separated)    │
│ NO  → Use Jackson               │
└─────────────────────────────────┘
             ↓
┌─────────────────────────────────┐
│ Use Jackson ObjectMapper        │
│ (complex types, POJOs, records) │
│ Throw SerializationException    │
│ on failure                      │
└─────────────────────────────────┘
```

**Performance Impact**:
- **Priority 1 (Primitives)**: Zero Jackson overhead; direct string conversion
- **Priority 2 (Byte types)**: Minimal overhead; base64 encoding (text-safe)
- **Priority 3 (Arrays)**: Minimal overhead; space/comma-separated values
- **Priority 4 (Jackson)**: Full JSON serialization; acceptable for non-real-time messaging

**Critical Rule**: Message payloads are ALWAYS text-based (UTF-8 or base64). Never raw binary data.

---

## Testing Your Implementation

### Manual Testing with NATS CLI

1. **Start NATS broker** (via docker-compose):
   ```bash
   docker-compose up nats
   ```

2. **Subscribe to messages** (in terminal 1):
   ```bash
   nats sub test
   ```

3. **Publish via Quarkus app** (in terminal 2):
   ```bash
   curl -X POST http://localhost:8080/typed-publisher/publish \
     -H "Content-Type: application/json" \
     -d '{"objectType":"java.lang.String","payload":"hello world"}'
   ```

4. **Verify message appears** (terminal 1 output):
   ```
   [1] Received on "test": hello world
   ```

### Testing CloudEvents

1. **Subscribe with raw output** (terminal 1):
   ```bash
   nats sub test --raw
   ```

2. **Publish CloudEvents** (terminal 2):
   ```bash
   curl -X POST http://localhost:8080/typed-publisher/publish-cloudevents \
     -H "Content-Type: application/json" \
     -d '{
       "objectType":"java.lang.String",
       "payload":"world",
       "ceType":"test.example.MyEvent",
       "ceSource":"/test"
     }'
   ```

3. **Verify CloudEvents headers** (terminal 1 output includes ce-* headers):
   ```
   [1] Received on "test":
   ce-specversion: 1.0
   ce-type: test.example.MyEvent
   ce-source: /test
   ce-id: 550e8400-e29b-41d4-a716-446655440000
   ce-time: 2025-10-26T14:30:45.123456Z
   ce-datacontenttype: application/json

   world
   ```

---

## Summary

| Feature | Usage | Priority | Notes |
|---------|-------|----------|-------|
| **Typed Publishing** | `publisher.publish(object)` | P1 | Type-safe, JSON serialization |
| **Primitive Types** | `NatsPublisher<String>`, `<Integer>`, etc. | P1 | Zero Jackson overhead; UTF-8 encoded |
| **Byte Types** | `NatsPublisher<byte[]>` | P1 | ALWAYS base64-encoded; never raw binary |
| **Primitive Arrays** | `NatsPublisher<int[]>`, `<String[]>` | P1 | Space/comma-separated; UTF-8 encoded |
| **Complex Types** | `NatsPublisher<MyEvent>` with `@RegisterForReflection` | P1 | Requires annotation for native image |
| **CloudEvents** | `publisher.publishCloudEvent(obj, type, source)` | P2 | Auto-generated ce-id, ce-time |
| **Error Handling** | Catch `IllegalArgumentException` or `SerializationException` | P1 | User-friendly messages |

---

## Next Steps

1. **Annotate your domain classes** with `@RegisterForReflection`
2. **Test with primitives** before moving to complex types
3. **Use CloudEvents** for event-driven patterns
4. **Monitor NATS CLI output** during development to verify message structure
5. **Run native image builds** to validate reflection metadata generation

For more details, see [contracts/typed-publisher-api.md](contracts/typed-publisher-api.md) and [data-model.md](data-model.md).
