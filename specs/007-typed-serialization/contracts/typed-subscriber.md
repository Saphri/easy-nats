# Contract: Typed Subscriber

**Feature**: 007-typed-serialization
**Date**: 2025-10-27

## Overview

The Typed Subscriber contract defines how users subscribe to messages with a specific type, with automatic deserialization and acknowledgment handling.

---

## Implementation Notes

**Annotation Stability**: The `@NatsSubscriber` annotation **remains unchanged**. This contract change only affects the **internal deserialization mechanism** (how messages are deserialized to types). Users experience no API changes.

**Internal Change**: Deserialization now uses **Jackson-only deserialization** instead of the previous strategy that supported native type handling for primitives and arrays. The public `@NatsSubscriber` annotation interface and method signatures are unaffected.

---

## API Interface

### @NatsSubscriber Annotation

**Purpose**: Mark a method as a typed message subscriber with automatic deserialization.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NatsSubscriber {
    /**
     * The NATS subject to subscribe to (required).
     */
    String value();
}
```

### Method Signature

```java
// Implicit mode: Framework auto-acks on success, auto-naks on error
@NatsSubscriber(subject = "orders")
public void handleOrder(OrderData order) {
    // Process order
    // Automatically acked on method return
    // Automatically naked if method throws exception
}

// Type parameter T must be Jackson-compatible
// Method must have exactly one parameter of type T
```

**Type Parameter T**:
- Must be a Jackson-compatible type
- Validated at subscriber registration (fail-fast)
- Can use Jackson annotations
- Can be a generic type (List<OrderData>, etc.)

**Method Constraints**:
- Must be public
- Must have exactly one parameter of type T
- Must have void return type
- Can throw any exception (will trigger NAK)
- Cannot be static

---

## Deserialization Process

### Step 1: Type Validation (at subscriber registration)

When application starts and `@NatsSubscriber` method is discovered:

1. Extract type parameter T from method parameter type
2. Run `ObjectMapper.getTypeFactory().constructType(T)`
3. Validate T is Jackson-compatible (no primitives, arrays, etc.)
4. If invalid, fail fast with clear error message:
   - Example: "Type `int` is not supported. Wrap it in a POJO"
   - Example: "Type `OrderData` requires a no-arg constructor or @JsonDeserialize"
5. If valid, proceed to subscriber registration
6. Cache validated MessageType<T> in subscriber bean

**Error Handling**:
- Type validation errors logged at subscriber registration (fail-fast)
- Clear error message directs user to wrapper pattern
- Application startup fails if type validation fails

### Step 2: Message Receipt (runtime)

When a message arrives on the subscribed subject:

1. CloudEvents binary-mode message received from NATS
2. Unwrap CloudEvents headers (internal detail)
3. Extract JSON payload bytes
4. Create DeserializationContext<T> with:
   - Validated MessageType<T>
   - Raw JSON payload bytes
   - Current timestamp for error logging

### Step 3: Deserialization (runtime)

When deserialization is needed:

1. Call `MessageDeserializer.deserialize(context, objectMapper)`
   - **Note**: Only Jackson deserialization is used (no native type handling)
   - Primitives and arrays must be wrapped in POJOs by users
2. Jackson deserializes JSON bytes to type T:
   - Respects Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize, etc.)
   - Uses standard Jackson default null handling
   - Validates required fields (per Jackson rules)
3. Return deserialized object of type T

**Error Handling** (deserialization failure):
- Catch `IOException` or `JsonMappingException`
- Wrap in `DeserializationException`
- Log error with:
  - Target type: T
  - Raw payload (first 1000 chars of JSON)
  - Root cause from Jackson exception
  - Actionable suggestion (e.g., "Type requires no-arg constructor")
- Automatically NAK the message (not acked)
- Subscriber method NOT invoked

### Step 4: Subscriber Invocation

If deserialization succeeds:

1. Call subscriber method with deserialized object
2. Method processes the message
3. On successful method return:
   - Automatically ACK the message
   - Subscriber method may process other messages
4. If method throws any exception:
   - Catch exception
   - Automatically NAK the message
   - Log exception details
   - Continue to next message
   - Do NOT propagate exception

---

## Example Usage

```java
// Define a POJO (Jackson-compatible)
public class OrderData {
    public String id;
    public BigDecimal amount;
    public String customerName;

    public OrderData() {}  // Required: no-arg constructor
    public OrderData(String id, BigDecimal amount, String customerName) {
        this.id = id;
        this.amount = amount;
        this.customerName = customerName;
    }
}

// Create subscriber
@ApplicationScoped
public class OrderListener {
    @NatsSubscriber(subject = "orders", consumer = "order-processor")
    public void handleOrder(OrderData order) {
        System.out.println("Processing order: " + order.id);
        // Automatically acked on successful return
        // Automatically nacked if exception thrown
    }
}
```

---

## Jackson Annotation Support

All standard Jackson annotations are supported in deserialization:

### @JsonProperty
```java
public class OrderData {
    @JsonProperty("order_id")  // Expects "order_id" in JSON
    public String id;

    @JsonProperty("total_amount")
    public BigDecimal amount;

    public OrderData() {}
}

// Deserializes from: {"order_id": "ORD-001", "total_amount": 150.00}
```

### @JsonIgnore
```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    @JsonIgnore
    public transient long processedAtMs;  // Not expected in JSON

    public OrderData() {}
}

// Deserializes from: {"id": "ORD-001", "amount": 150.00}
// processedAtMs will be 0 (default value)
```

### @JsonDeserialize
```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    @JsonDeserialize(using = CustomDateDeserializer.class)
    public LocalDate orderDate;

    public OrderData() {}
}

// Custom deserializer controls how orderDate is parsed from JSON
// Example: Custom deserializer converts "2025-10-27" → LocalDate object
```

### @JsonCreator + @JsonProperty
```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    @JsonCreator
    public OrderData(
        @JsonProperty("order_id") String id,
        @JsonProperty("total_amount") BigDecimal amount
    ) {
        this.id = id;
        this.amount = amount;
    }
}

// Deserializes from: {"order_id": "ORD-001", "total_amount": 150.00}
// @JsonCreator tells Jackson to use this constructor (instead of no-arg constructor)
```

---

## Error Cases

### Case 1: Unsupported Type (Primitive)

```java
// This fails at subscriber registration:
@NatsSubscriber(subject = "numbers", consumer = "number-processor")
public void handleNumber(Integer number) {  // ❌ FAILS
    System.out.println("Number: " + number);
}

// Error: "Primitive type 'Integer' cannot be used directly. Wrap in a POJO:
// public class IntValue { public int value; public IntValue() {} }"
```

**Solution**: Create wrapper

```java
public class IntValue {
    public int value;
    public IntValue() {}
    public IntValue(int value) { this.value = value; }
}

@NatsSubscriber(subject = "numbers", consumer = "number-processor")
public void handleNumber(IntValue data) {  // ✅ Works
    System.out.println("Number: " + data.value);
}
```

### Case 2: Deserialization Failure (Malformed JSON)

```java
// At runtime, if message is not valid JSON:
// Raw message: "not-json"

// Result:
// - DeserializationException logged:
//   "Failed to deserialize to type OrderData:
//    Unexpected character ('n' (code 110)): expected '{' to start OBJECT value"
// - Raw payload logged: "not-json"
// - Message automatically NAK'd
// - Subscriber method NOT invoked
```

**Prevention**: Validate message format at publisher

### Case 3: Deserialization Failure (Type Mismatch)

```java
// Type definition requires fields that JSON doesn't have:
public class OrderData {
    @JsonProperty(required = true)  // Field is required
    public String id;

    public OrderData() {}
}

// At runtime, if JSON is missing "id":
// Raw message: {"amount": 150.00}

// Result:
// - DeserializationException logged:
//   "Failed to deserialize to type OrderData:
//    Missing required creator property 'id' (index 0);
//    [source: {\"amount\": 150.00}; line: 1, column: 27]"
// - Raw payload logged: {"amount": 150.00}
// - Message automatically NAK'd
// - Subscriber method NOT invoked
```

**Prevention**: Validate JSON structure at publisher

### Case 4: Deserialization Failure (No No-Arg Constructor)

```java
// Type without no-arg constructor (and no custom deserializer):
public class OrderData {
    public String id;
    public BigDecimal amount;

    public OrderData(String id, BigDecimal amount) {  // ❌ No no-arg constructor
        this.id = id;
        this.amount = amount;
    }
}

// At subscriber registration:
// Error: "Type 'OrderData' requires a no-arg constructor for Jackson deserialization.
// Add a no-arg constructor or use @JsonDeserialize with a custom deserializer."
```

**Solution 1**: Add no-arg constructor

```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    public OrderData() {}  // No-arg constructor added
    public OrderData(String id, BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }
}
```

**Solution 2**: Use @JsonDeserialize with custom deserializer

```java
@JsonDeserialize(using = OrderDataDeserializer.class)
public class OrderData {
    public String id;
    public BigDecimal amount;

    public OrderData(String id, BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }
}

public class OrderDataDeserializer extends StdDeserializer<OrderData> {
    public OrderDataDeserializer() { super(OrderData.class); }

    @Override
    public OrderData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // Custom deserialization logic here
        JsonNode node = p.getCodec().readTree(p);
        String id = node.get("id").asText();
        BigDecimal amount = new BigDecimal(node.get("amount").asText());
        return new OrderData(id, amount);
    }
}
```

### Case 5: Subscriber Method Throws Exception

```java
@NatsSubscriber(subject = "orders", consumer = "order-processor")
public void handleOrder(OrderData order) {
    if (order.amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Order amount must be positive");  // ❌ Throws
    }
    // Process order
}

// At runtime, if exception is thrown:
// - Exception is logged: "Error processing message for subscriber: ..."
// - Message is automatically NAK'd (not acked)
// - Subscriber continues to next message
// - Exception does NOT propagate
```

**Prevention**: Validate in subscriber

```java
@NatsSubscriber(subject = "orders", consumer = "order-processor")
public void handleOrder(OrderData order) {
    if (order.amount.compareTo(BigDecimal.ZERO) <= 0) {
        logger.error("Invalid order amount: {}", order.amount);
        return;  // Implicit NAK happens on exception; we prevent the exception
    }
    // Process order
}
```

---

## Acknowledgment Behavior

| Scenario | Behavior |
|----------|----------|
| Deserialization succeeds, method returns normally | ACK (message acknowledged) |
| Deserialization succeeds, method throws exception | NAK (message not acknowledged) |
| Deserialization fails | NAK (message not acknowledged, method not invoked) |
| Type validation fails at startup | Application fails to start |

---

## Performance Characteristics

| Aspect | Behavior |
|--------|----------|
| Type validation | Once at app startup (negligible impact) |
| Deserialization | Standard Jackson performance (microseconds) |
| ACK/NAK | Native NATS JetStream operations (microseconds) |
| Consumer creation | Once at startup or on first message (internal) |
| Memory | Jackson reuses ObjectMapper (no per-subscriber overhead) |

---

## Testing Requirements

Subscribers MUST be tested with:
1. Valid JSON messages with Jackson-compatible types
2. Malformed JSON (expect NAK)
3. Missing required fields (expect NAK)
4. Type mismatches (expect NAK)
5. Subscriber method exceptions (expect NAK)
6. Messages with Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize)
7. Null field values (standard Jackson null handling)
8. Concurrent message processing (JetStream consumer semantics)

---

## Future Enhancements (Out of Scope)

- Explicit mode with NatsMessage<T> wrapper (can be added in future)
- Batch processing of multiple messages (currently one-at-a-time)
- Custom acknowledgment strategies (currently auto-ack/auto-nak only)
- Message filtering (currently all messages on subject processed)
