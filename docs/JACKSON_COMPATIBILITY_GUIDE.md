# Jackson Compatibility Guide - Quarkus EasyNATS

This guide explains which Java types are compatible with Quarkus EasyNATS for publish/subscribe operations and which types require wrapping.

## What Types Are Supported

Quarkus EasyNATS supports **any type that Jackson can serialize and deserialize**. This includes:

- **POJOs** (Plain Old Java Objects) with a no-arg constructor
- **Java Records** (Java 14+, with automatic no-arg constructor support)
- **Standard Java types**: `String`, `BigDecimal`, `LocalDate`, `LocalDateTime`, etc.
- **Collections**: `List<T>`, `Map<K,V>`, `Set<T>` (where T is a supported type)
- **Custom types** with Jackson annotations for customization

The key requirement: **The type must be instantiable by Jackson's ObjectMapper**.

---

## POJOs with No-Arg Constructor

A POJO (Plain Old Java Object) is the standard way to define message types. The library requires a **no-arg constructor** for deserialization.

### Simple POJO Example

```java
public class OrderData {
    private String orderId;
    private String customerId;
    private BigDecimal totalAmount;

    // No-arg constructor (required for Jackson deserialization)
    public OrderData() {
    }

    // Convenience constructor for easy creation
    public OrderData(String orderId, String customerId, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }

    // Getters and setters (required for Jackson)
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
```

### Using the POJO

```java
// Publishing
@Dependent
public class OrderService {
    private final NatsPublisher<OrderData> publisher;

    public OrderService(NatsPublisher<OrderData> publisher) {
        this.publisher = publisher;
    }

    public void publishOrder(OrderData order) throws Exception {
        publisher.publish("orders", order);
    }
}

// Subscribing
@Singleton
public class OrderListener {
    private OrderData lastOrder;

    @NatsSubscriber("orders")
    public void handleOrder(OrderData order) {
        this.lastOrder = order;
        System.out.println("Received order: " + order.getOrderId());
    }

    public OrderData getLastOrder() {
        return lastOrder;
    }
}
```

---

## Java Records

Java Records (Java 14+) are perfect for message types. Records automatically:
- Generate a no-arg constructor (in Java 17+)
- Generate getters
- Generate `equals()`, `hashCode()`, and `toString()`
- Are immutable (great for message types)

### Record Example

```java
public record ShipmentNotification(
    String shipmentId,
    String trackingNumber,
    LocalDateTime estimatedDelivery,
    String status
) {
}
```

### Using Records

```java
// Publishing
public void publishShipment(ShipmentNotification notification) throws Exception {
    publisher.publish("shipments", notification);
}

// Subscribing
@NatsSubscriber("shipments")
public void handleShipment(ShipmentNotification notification) {
    System.out.println("Shipment " + notification.shipmentId() +
                       " will arrive by " + notification.estimatedDelivery());
}
```

**Benefit**: Records eliminate boilerplate while maintaining type safety and immutability.

---

## Generic Types

The library supports generic collection types as long as the type parameters are themselves supported types.

### List Example

```java
// Type parameter OrderData must be a supported type
NatsPublisher<List<OrderData>> batchPublisher = /* ... */;

List<OrderData> orders = List.of(
    new OrderData("ORD-001", "CUST-001", new BigDecimal("99.99")),
    new OrderData("ORD-002", "CUST-002", new BigDecimal("149.99"))
);

batchPublisher.publish("order-batch", orders);
```

### Map Example

```java
// Both key and value types must be supported
NatsPublisher<Map<String, OrderData>> mapPublisher = /* ... */;

Map<String, OrderData> ordersByCustomer = Map.of(
    "CUST-001", new OrderData("ORD-001", "CUST-001", new BigDecimal("99.99")),
    "CUST-002", new OrderData("ORD-002", "CUST-002", new BigDecimal("149.99"))
);

mapPublisher.publish("customer-orders", ordersByCustomer);
```

### Set Example

```java
// Set of supported types works transparently
NatsPublisher<Set<String>> tagPublisher = /* ... */;

Set<String> orderTags = Set.of("urgent", "express", "tracked");
tagPublisher.publish("order-tags", orderTags);
```

---

## Jackson Annotations for Customization

Standard Jackson annotations work transparently with Quarkus EasyNATS. Use these to customize serialization behavior without changing the library code.

### @JsonProperty - Custom Field Names

Rename fields in JSON without changing Java field names:

```java
public class OrderData {
    @JsonProperty("order_id")  // JSON will use "order_id" instead of "orderId"
    private String orderId;

    @JsonProperty("customer_id")
    private String customerId;

    private BigDecimal totalAmount;

    // No-arg and other constructors omitted for brevity
}
```

Published JSON will look like:
```json
{
  "order_id": "ORD-001",
  "customer_id": "CUST-001",
  "totalAmount": 99.99
}
```

### @JsonIgnore - Exclude Fields

Exclude sensitive or transient fields from serialization:

```java
public class UserData {
    private String username;
    private String email;

    @JsonIgnore  // This field is never serialized to JSON
    private String password;

    @JsonIgnore  // Internal tracking not sent to other services
    private String internalId;

    // Constructors and accessors
}
```

Subscribed JSON will never contain `password` or `internalId` fields.

### @JsonDeserialize - Custom Deserialization

Use a custom deserializer for complex types:

```java
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

public class DateRange {
    @JsonDeserialize(using = DateDeserializer.class)
    private LocalDate startDate;

    private LocalDate endDate;

    // No-arg constructor required
    public DateRange() {
    }

    // Getters/setters
}

public class DateDeserializer extends StdDeserializer<LocalDate> {
    public DateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String date = p.getText();
        // Custom parsing logic
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }
}
```

### @JsonSerialize - Custom Serialization

Use a custom serializer to format fields during serialization:

```java
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class Price {
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal amount;

    private String currency;

    // Constructors and accessors
}

public class MoneySerializer extends StdSerializer<BigDecimal> {
    public MoneySerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(String.format("%.2f", value));  // Format as "99.99"
    }
}
```

---

## What Types Are NOT Supported

Quarkus EasyNATS explicitly rejects certain types that Jackson cannot handle:

### Primitive Types

Primitive types (`int`, `long`, `double`, `boolean`, etc.) cannot be used directly because they don't have no-arg constructors.

**❌ WRONG - Will be rejected:**
```java
NatsPublisher<Integer> publisher;  // Compile error (type parameter cannot be primitive)
NatsPublisher<int> publisher;      // Compile error

@NatsSubscriber("numbers")
public void handleNumber(int value) {  // Build-time error: primitive type not supported
}
```

**✅ CORRECT - Wrap in POJO:**
```java
public class IntValue {
    private int value;

    public IntValue() {
    }

    public IntValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}

// Now you can use it:
NatsPublisher<IntValue> publisher;

@NatsSubscriber("numbers")
public void handleNumber(IntValue container) {
    int value = container.getValue();
}
```

### Array Types

Array types (`int[]`, `String[]`, etc.) are not supported.

**❌ WRONG:**
```java
NatsPublisher<int[]> publisher;        // Runtime error
NatsPublisher<String[]> publisher;     // Runtime error

@NatsSubscriber("values")
public void handleValues(String[] values) {  // Build-time error
}
```

**✅ CORRECT - Wrap in POJO:**
```java
public class StringList {
    private String[] items;

    public StringList() {
    }

    public StringList(String[] items) {
        this.items = items;
    }

    public String[] getItems() {
        return items;
    }

    public void setItems(String[] items) {
        this.items = items;
    }
}

// Now you can use it:
NatsPublisher<StringList> publisher;

@NatsSubscriber("values")
public void handleValues(StringList container) {
    String[] values = container.getItems();
}
```

### Types Without No-Arg Constructor

Classes that only have constructors requiring parameters cannot be deserialized by Jackson.

**❌ WRONG:**
```java
public class Order {
    private String id;
    private String customer;

    // Only constructor requires parameters - no no-arg constructor
    public Order(String id, String customer) {
        this.id = id;
        this.customer = customer;
    }
}

NatsPublisher<Order> publisher;  // Runtime error on first publish
```

**✅ CORRECT - Add no-arg constructor:**
```java
public class Order {
    private String id;
    private String customer;

    // Add this no-arg constructor
    public Order() {
    }

    public Order(String id, String customer) {
        this.id = id;
        this.customer = customer;
    }

    // Getters and setters
}

NatsPublisher<Order> publisher;  // Now works
```

---

## Type Validation

The library validates types at two stages:

### Build-Time Validation (Subscriber Methods)

When you annotate a method with `@NatsSubscriber`, the build-time processor validates the parameter type:

```java
@NatsSubscriber("orders")
public void handleOrder(int orderId) {  // ❌ Build fails immediately
    // Error: Primitive type 'int' is not supported...
}
```

**Benefit**: Catch type errors before runtime.

### Runtime Validation (Publisher)

When you first call `publisher.publish()`, the library validates the generic type:

```java
NatsPublisher<Integer> publisher;  // Type not immediately validated

publisher.publish("numbers", 42);  // ❌ Runtime error on first publish
// Error: Invalid type 'Integer' for NatsPublisher: Primitive type...
```

**Benefit**: Clear error message with wrapper pattern example.

---

## Best Practices

1. **Use Records for Simple Types**: Records eliminate boilerplate and are immutable.

   ```java
   public record OrderEvent(
       String orderId,
       Instant timestamp,
       BigDecimal amount
   ) {}
   ```

2. **Always Include No-Arg Constructor**: Even with Records and POJOs, ensure Jackson can instantiate them.

3. **Use Jackson Annotations for Customization**: Don't create wrapper types for field naming—use `@JsonProperty`.

   ```java
   // ✅ Good: Use annotations
   public class Order {
       @JsonProperty("order_id")
       private String orderId;
   }

   // ❌ Bad: Creating wrapper classes for every variation
   public class OrderWithRenamedId { ... }
   ```

4. **Keep Message Types Simple**: Message types should represent data, not behavior.

5. **Document Custom Serialization**: When using `@JsonSerialize` or `@JsonDeserialize`, document the format.

---

## Common Errors and Solutions

| Error | Cause | Solution |
|-------|-------|----------|
| `Primitive type 'int' is not supported` | Using `int` directly | Wrap in `IntValue` POJO |
| `Array type '[I' is not supported` | Using `int[]` directly | Wrap in POJO with `int[]` field |
| `requires a no-arg constructor` | Missing no-arg constructor | Add `public ClassName() {}` |
| `Failed to deserialize: Unrecognized field` | JSON has extra fields | Use `@JsonIgnore` or `@JsonAnySetter` |
| `Failed to serialize: No serializer found` | Custom type needs serializer | Use `@JsonSerialize(using = ...)` |

---

## Next Steps

- See [WRAPPER_PATTERN.md](./WRAPPER_PATTERN.md) for detailed wrapping examples
- See [ERROR_TROUBLESHOOTING.md](./ERROR_TROUBLESHOOTING.md) for error resolution
- See [JACKSON_ANNOTATIONS_GUIDE.md](./JACKSON_ANNOTATIONS_GUIDE.md) for annotation deep-dive
