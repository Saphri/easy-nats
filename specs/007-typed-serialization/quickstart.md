# Quick Start: Typed Message Serialization

**Feature**: 007-typed-serialization
**Date**: 2025-10-27

## 5-Minute Guide

Learn how to publish and subscribe to strongly-typed messages in EasyNATS with automatic JSON serialization/deserialization.

---

## Step 1: Define a Message Type

Create a simple POJO (Plain Old Java Object) with a **no-arg constructor**:

```java
import java.math.BigDecimal;

public class OrderData {
    public String id;
    public String customerName;
    public BigDecimal amount;

    // Required: no-arg constructor for Jackson
    public OrderData() {}

    // Optional: convenience constructor
    public OrderData(String id, String customerName, BigDecimal amount) {
        this.id = id;
        this.customerName = customerName;
        this.amount = amount;
    }
}
```

Or use a **Java record** (Java 14+):

```java
import java.math.BigDecimal;

public record OrderData(
    String id,
    String customerName,
    BigDecimal amount
) {}
```

**Key Requirements**:
- ✅ Public no-arg constructor (for POJOs)
- ✅ Records work out-of-the-box
- ❌ No primitives (wrap in POJO)
- ❌ No arrays (wrap in POJO)
- ❌ No types without no-arg constructor

---

## Step 2: Publish Typed Messages

Inject a `TypedPublisher<T>` and publish:

```java
import javax.inject.Inject;
import io.quarkus.arc.Singleton;

@Singleton
public class OrderService {
    @Inject
    @NatsSubject("orders")
    TypedPublisher<OrderData> orderPublisher;

    public void publishOrder(String id, String customer, BigDecimal amount) {
        OrderData order = new OrderData(id, customer, amount);
        orderPublisher.publish(order);
        // Message automatically serialized to JSON and sent to NATS
    }
}
```

**What Happens**:
1. `OrderData` instance is automatically serialized to JSON
2. JSON is sent to NATS JetStream on subject `orders`
3. If serialization fails, `SerializationException` is thrown

---

## Step 3: Subscribe to Typed Messages

Mark a method with `@NatsSubscriber`:

```java
import javax.inject.Inject;
import io.quarkus.arc.Singleton;

@Singleton
public class OrderListener {
    @NatsSubscriber(subject = "orders", consumer = "order-processor")
    public void handleOrder(OrderData order) {
        System.out.println("Received order: " + order.id);
        System.out.println("Customer: " + order.customerName);
        System.out.println("Amount: " + order.amount);

        // Process the order
        // Message is automatically acked on success
        // If this method throws, message is automatically nacked (redelivered)
    }
}
```

**What Happens**:
1. JSON message is automatically deserialized to `OrderData`
2. `handleOrder()` is invoked with the typed object
3. On successful return, message is **ACK'd** (consumed)
4. If method throws exception, message is **NAK'd** (redelivered)

---

## Step 4: Handle Unsupported Types

If you have a type that's not Jackson-compatible (e.g., a primitive `int`), **wrap it in a POJO**:

```java
// ❌ This doesn't work:
// @NatsSubscriber(subject = "counts", consumer = "counter")
// public void handleCount(int count) {}  // FAILS

// ✅ Do this instead:
public class CountData {
    public int count;
    public CountData() {}
    public CountData(int count) { this.count = count; }
}

@NatsSubscriber(subject = "counts", consumer = "counter")
public void handleCount(CountData data) {
    System.out.println("Count: " + data.count);
}
```

---

## Complete Example

### 1. Message Type

```java
import java.math.BigDecimal;

public record OrderData(
    String id,
    String customerName,
    BigDecimal amount
) {}
```

### 2. Service

```java
import javax.inject.Inject;
import io.quarkus.arc.Singleton;

@Singleton
public class OrderService {
    @Inject
    @NatsSubject("orders")
    TypedPublisher<OrderData> orderPublisher;

    public void createOrder(String customerId, BigDecimal totalAmount) {
        OrderData order = new OrderData(
            "ORD-" + System.currentTimeMillis(),
            customerId,
            totalAmount
        );
        orderPublisher.publish(order);
        System.out.println("Order published: " + order.id);
    }
}
```

### 3. Listener

```java
import javax.inject.Inject;
import io.quarkus.arc.Singleton;

@Singleton
public class OrderListener {
    @Inject
    OrderService orderService;  // Can inject other services

    @NatsSubscriber(subject = "orders", consumer = "order-processor")
    public void processOrder(OrderData order) {
        System.out.println("Processing order: " + order.id);
        System.out.println("Customer: " + order.customerName);
        System.out.println("Amount: $" + order.amount);

        // Business logic here
        // ...

        System.out.println("Order processed!");
        // Automatically acked on successful return
    }
}
```

### 4. REST Endpoint (optional)

```java
import javax.inject.Inject;
import javax.ws.rs.*;
import io.quarkus.rest.common.Path;
import java.math.BigDecimal;

@Path("/orders")
public class OrderResource {
    @Inject
    OrderService orderService;

    @POST
    public void createOrder(
        @QueryParam("customer") String customerId,
        @QueryParam("amount") BigDecimal amount
    ) {
        orderService.createOrder(customerId, amount);
    }
}
```

### 5. Test It

```bash
# Publish an order
curl -X POST "http://localhost:8080/orders?customer=CUST-001&amount=150.00"

# Output in logs:
# Order published: ORD-1698444000000
# Processing order: ORD-1698444000000
# Customer: CUST-001
# Amount: $150.00
# Order processed!
```

---

## Jackson Annotations

Customize serialization/deserialization with standard Jackson annotations:

### @JsonProperty - Rename Fields

```java
public class OrderData {
    @JsonProperty("order_id")  // Maps to "order_id" in JSON
    public String id;

    @JsonProperty("total_amount")
    public BigDecimal amount;

    public OrderData() {}
}

// JSON: {"order_id": "ORD-001", "total_amount": 150.00}
```

### @JsonIgnore - Skip Fields

```java
public class OrderData {
    public String id;
    public BigDecimal amount;

    @JsonIgnore  // Not included in serialization
    public transient long createdAtMs;

    public OrderData() {}
}

// JSON: {"id": "ORD-001", "amount": 150.00}
// (createdAtMs is not serialized)
```

### @JsonDeserialize - Custom Deserialization

```java
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;

public class OrderData {
    public String id;

    @JsonDeserialize(using = CustomDateDeserializer.class)
    public LocalDate orderDate;

    public OrderData() {}
}

// Custom deserializer controls date parsing
```

---

## Common Patterns

### Pattern 1: Multiple Subscribers for Different Consumers

```java
@Singleton
public class OrderListeners {
    @NatsSubscriber(subject = "orders", consumer = "order-processor")
    public void processOrder(OrderData order) {
        // Primary processing
    }

    @NatsSubscriber(subject = "orders", consumer = "order-auditor")
    public void auditOrder(OrderData order) {
        // Audit logging
        // Multiple consumers can subscribe to same subject independently
    }
}
```

### Pattern 2: Dependent Types

```java
public record OrderLine(String productId, int quantity) {}

public record OrderData(
    String id,
    String customerName,
    List<OrderLine> lines  // Generic types work!
) {}

// JSON: {"id":"ORD-001", "customerName":"John", "lines":[{"productId":"P1","quantity":5}]}
```

### Pattern 3: Error Handling via Wrapper

If you need to handle deserialization errors:

```java
// Create wrapper with validation
public class SafeOrderData {
    public OrderData data;
    public String error;

    public SafeOrderData() {}
}

// Or use custom deserializer with fallback
public class SafeOrderDeserializer extends StdDeserializer<OrderData> {
    @Override
    public OrderData deserialize(JsonParser p, DeserializationContext ctxt) {
        try {
            // Normal deserialization
            return objectMapper.readValue(p, OrderData.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize order", e);
            return new OrderData("ERROR", "Unknown", BigDecimal.ZERO);
        }
    }
}
```

---

## Troubleshooting

### Error: "Type 'int' is not supported"

**Problem**: Trying to use a primitive type

**Solution**: Wrap in a POJO
```java
public class IntValue {
    public int value;
    public IntValue() {}
}
```

### Error: "Missing no-arg constructor"

**Problem**: Type doesn't have a no-arg constructor

**Solution**: Add one
```java
public OrderData() {}
```

### Message not being deserialized

**Problem**: JSON structure doesn't match type

**Solution**: Ensure JSON has all required fields
```java
// Type expects: id, customerName, amount
// JSON must have: {"id": "...", "customerName": "...", "amount": ...}
```

### Serialization fails with "Infinite recursion"

**Problem**: Object has circular reference

**Solution**: Break the cycle with `@JsonIgnore`
```java
public class Order {
    public String id;

    @JsonIgnore  // Don't serialize the back-reference
    public Customer customer;

    public Order() {}
}
```

---

## Full Documentation

### Type Support & Compatibility
- **[Jackson Compatibility Guide](./JACKSON_COMPATIBILITY_GUIDE.md)** - Which types are supported, POJOs, Records, Generics, and Jackson annotations overview

### Handling Unsupported Types
- **[Wrapper Pattern Guide](./WRAPPER_PATTERN.md)** - How to wrap primitives, arrays, and types without no-arg constructors with complete examples

### Troubleshooting
- **[Error Troubleshooting Guide](./ERROR_TROUBLESHOOTING.md)** - Common errors, their causes, and step-by-step solutions with code examples

### Advanced Topics
- **[Jackson Annotations Deep Dive](./JACKSON_ANNOTATIONS_GUIDE.md)** - Comprehensive guide to using Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize, @JsonSerialize, etc.) for customization

## API & Architecture

- Read the **[Typed Publisher Contract](./contracts/typed-publisher.md)** for detailed API documentation
- Read the **[Typed Subscriber Contract](./contracts/typed-subscriber.md)** for error handling details
- Read the **[Error Handling Contract](./contracts/errors.md)** for exception details
- Check the **[Data Model](./data-model.md)** for type validation rules
- See **[Feature Specification](./spec.md)** for complete requirements

---

## Tips & Best Practices

✅ **Do**:
- Use records for simple message types (concise, immutable)
- Use Jackson annotations for custom serialization
- Validate data in subscriber methods before processing
- Handle exceptions in subscriber to prevent message NAK

❌ **Don't**:
- Don't use primitives directly (wrap in POJO)
- Don't use arrays directly (wrap in POJO)
- Don't rely on field order in JSON (use named parameters)
- Don't use custom ObjectMapper (library provides one CDI bean)
- Don't catch exceptions in subscriber expecting message ACK (will NAK instead)

