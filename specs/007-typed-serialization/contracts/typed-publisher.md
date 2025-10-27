# Contract: Typed Publisher

**Feature**: 007-typed-serialization
**Date**: 2025-10-27

## Overview

The Typed Publisher contract defines how users publish strongly-typed messages to NATS JetStream without manual serialization.

---

## API Interface

### NatsPublisher<T>

**Purpose**: Publish instances of a specific type T to a NATS subject.

**Public API** (unchanged from existing implementation):

```java
public class NatsPublisher<T> {
    /**
     * Publishes a typed payload to the default NATS subject as a CloudEvent.
     * The default subject must be configured via @NatsSubject.
     *
     * @param payload the object to publish (must not be null)
     * @throws IllegalArgumentException if payload is null
     * @throws PublishingException if the default subject is not configured or if publication fails
     */
    public void publish(T payload) throws PublishingException { }

    /**
     * Publishes a typed payload to the specified NATS subject as a CloudEvent.
     *
     * @param subject the NATS subject to publish to
     * @param payload the object to publish (must not be null)
     * @throws PublishingException if publication fails (payload is null, serialization error, connection error, etc.)
     */
    public void publish(String subject, T payload) throws PublishingException { }
}
```

**Type Parameter**:
- `T` must be a Jackson-compatible type:
  - POJO with public no-arg constructor
  - Java record
  - Generic type (List<T>, Map<K, V>, etc.)
  - Type with Jackson annotations (`@JsonDeserialize`, `@JsonProperty`, etc.)

**Constraints**:
- `T` cannot be a primitive type (int, long, double, etc.)
- `T` cannot be an array type (int[], String[], etc.)
- `T` cannot be a type without a no-arg constructor unless using `@JsonDeserialize`

---

## Implementation Notes

**Public API Stability**: The `NatsPublisher<T>` public interface (`publish(T)` and `publish(String, T)`) **remains unchanged**. This contract change only affects the **internal encoding mechanism** (how payloads are serialized). Users experience no API changes.

**Internal Change**: The `encodePayload()` private method now uses **Jackson-only serialization** instead of the previous strategy that supported native type handling for primitives and arrays.

---

## Serialization Process

### Step 1: Type Validation (at publisher creation)

When `@Inject @NatsSubject("subject-name") TypedPublisher<T> publisher` is injected:

1. Extract generic type parameter T from field type
2. Run `ObjectMapper.getTypeFactory().constructType(T)`
3. Validate T is Jackson-compatible (no primitives, arrays, etc.)
4. If invalid, fail fast with clear error message:
   - Example: "Type `int` is not supported. Wrap it in a POJO"
   - Example: "Type `OrderData` requires a no-arg constructor or @JsonDeserialize"
5. If valid, proceed to publisher instantiation
6. Cache validated MessageType<T> in publisher bean

**Error Handling**:
- Type validation errors logged at publisher injection time (fail-fast)
- Clear error message directs user to wrapper pattern
- Application startup fails if type validation fails

### Step 2: Serialization (at publish time)

When `publisher.publish(payload)` is called:

1. Verify payload is instance of T (should always be true due to generics)
2. Call `TypedPayloadEncoder.encodeWithJackson(payload, objectMapper)`
   - **Note**: Only Jackson serialization is used (no native type handling)
   - Primitives and arrays must be wrapped in POJOs by users
3. Jackson serializes payload to JSON bytes:
   - Respects Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize, etc.)
   - Uses standard Jackson default null handling (WRITE_NULL by default)
   - Uses default Jackson date/time formatting
4. Wrap JSON bytes in CloudEvents binary-mode format (internal detail)
5. Publish to NATS JetStream

**Error Handling**:
- If serialization fails:
  - Catch `JsonProcessingException`
  - Wrap in `SerializationException` with message: "Failed to serialize [TypeName]: [Jackson error]"
  - Include original Jackson exception as cause
  - Log error with type name for debugging
  - Throw to caller (application must handle)

---

## Example Usage

```java
// Define a POJO (Jackson-compatible)
public class OrderData {
    public String id;
    public BigDecimal amount;

    public OrderData() {}  // Required: no-arg constructor
    public OrderData(String id, BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }
}

// Inject typed publisher
@ApplicationScoped
public class OrderService {
    @Inject
    @NatsSubject("orders")
    TypedPublisher<OrderData> orderPublisher;

    public void publishOrder(String id, BigDecimal amount) throws SerializationException {
        OrderData order = new OrderData(id, amount);
        orderPublisher.publish(order);  // Automatic JSON serialization
        // Message is now in NATS as JSON, wrapped in CloudEvents
    }
}
```

---

## Jackson Annotation Support

All standard Jackson annotations are supported:

### @JsonProperty
```java
public class OrderData {
    @JsonProperty("order_id")  // Maps to "order_id" in JSON
    public String id;

    @JsonProperty("total_amount")
    public BigDecimal amount;

    public OrderData() {}
}

// Serialized as: {"order_id": "ORD-001", "total_amount": 150.00}
```

### @JsonIgnore
```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    @JsonIgnore
    public transient long createdAtMs;  // Not included in JSON

    public OrderData() {}
}

// Serialized as: {"id": "ORD-001", "amount": 150.00}
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
```

### @JsonSerialize
```java
public class OrderData {
    public String id;

    @JsonSerialize(using = CustomPriceSerializer.class)
    public BigDecimal price;

    public OrderData() {}
}

// Custom serializer controls how price is encoded to JSON
```

---

## Error Cases

### Case 1: Unsupported Type (Primitive)

```java
// This fails at publisher injection time:
@Inject
@NatsSubject("numbers")
TypedPublisher<Integer> numberPublisher;  // ❌ FAILS

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

@Inject
@NatsSubject("numbers")
TypedPublisher<IntValue> numberPublisher;  // ✅ Works

numberPublisher.publish(new IntValue(42));
```

### Case 2: Unsupported Type (Array)

```java
// This fails at publisher injection time:
@Inject
@NatsSubject("lists")
TypedPublisher<String[]> listPublisher;  // ❌ FAILS

// Error: "Array type 'String[]' is not supported. Wrap in a POJO:
// public class StringList { public String[] items; public StringList() {} }"
```

**Solution**: Create wrapper

```java
public class StringList {
    public String[] items;
    public StringList() {}
    public StringList(String[] items) { this.items = items; }
}

@Inject
@NatsSubject("lists")
TypedPublisher<StringList> listPublisher;  // ✅ Works

listPublisher.publish(new StringList(new String[]{"a", "b", "c"}));
```

### Case 3: Serialization Failure (Circular Reference)

```java
// At publish time, if payload has circular reference:
orderPublisher.publish(order);

// Throws SerializationException:
// "Failed to serialize OrderData: Infinite recursion with class Item"
```

**Solution**: Restructure data to break cycle or use `@JsonIgnore`

```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    @JsonIgnore  // Don't serialize the back-reference
    public Customer customer;

    public OrderData() {}
}

orderPublisher.publish(order);  // ✅ Works
```

### Case 4: Null Payload

```java
// If payload is null, Jackson serializes as "null" JSON:
orderPublisher.publish(null);

// Message in NATS: the literal JSON "null"
// This is usually a user error; application should validate before publishing
```

**Prevention**: Application-level validation

```java
public void publishOrder(OrderData order) throws IllegalArgumentException, SerializationException {
    if (order == null) {
        throw new IllegalArgumentException("Order cannot be null");
    }
    orderPublisher.publish(order);
}
```

---

## Performance Characteristics

| Aspect | Behavior |
|--------|----------|
| Type validation | Once at publisher creation (negligible impact) |
| Serialization | Standard Jackson performance (microseconds for typical POJOs) |
| Memory | Jackson reuses ObjectMapper across all publishers (no per-type overhead) |
| Network | JSON payload size depends on object structure (Jackson is space-efficient) |

---

## Testing Requirements

Publishers MUST be tested with:
1. Valid Jackson-compatible types (POJO, record, generic)
2. Types with Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize)
3. Null payloads
4. Large payloads
5. Circular reference detection (expect SerializationException)
6. Custom Jackson serializers (@JsonSerialize)

---

## Future Enhancements (Out of Scope)

- Custom ObjectMapper per publisher (use Quarkus config instead)
- Per-type Jackson modules (use annotations instead)
- Async publish (currently synchronous; can be added in future release)
