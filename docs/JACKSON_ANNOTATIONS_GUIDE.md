# Jackson Annotations with Quarkus EasyNATS

## Overview

This library **does not implement** custom serialization/deserialization logic. Instead, it delegates directly to Jackson's `ObjectMapper`, which means **all standard Jackson annotations work transparently**.

This guide shows how to use Jackson annotations to customize JSON serialization/deserialization with Quarkus EasyNATS.

### Key Principle

> The library uses Jackson's `ObjectMapper` directly. Jackson handles all annotation processing. The library just passes data to Jackson's `writeValueAsBytes()` and `readValue()` methods.

## How It Works

### Serialization Pipeline (Publisher)

```
Your POJO with annotations
    ↓
Library calls: ObjectMapper.writeValueAsBytes(object)
    ↓
Jackson processes all annotations (@JsonProperty, @JsonIgnore, @JsonSerialize, etc.)
    ↓
JSON bytes produced (annotations effects baked in)
    ↓
CloudEvents binary-mode wrapping (headers + JSON body unchanged)
    ↓
NATS transmission
```

### Deserialization Pipeline (Subscriber)

```
NATS message with CloudEvents headers
    ↓
CloudEvents binary-mode unwrapping (headers removed, JSON extracted)
    ↓
JSON bytes unchanged (annotations effects preserved)
    ↓
Library calls: ObjectMapper.readValue(jsonBytes, YourClass.class)
    ↓
Jackson processes all annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize, etc.)
    ↓
Your POJO instance created with correct field mapping
```

### CloudEvents Binary-Mode

With binary-mode CloudEvents (used by this library), the message body is **pure JSON from Jackson**, completely separate from CloudEvents headers. This means:

- ✅ Annotations work transparently - Jackson processes them independently
- ✅ No interference between headers and JSON body
- ✅ JSON can be validated independently of CloudEvents
- ✅ Headers are metadata only, cannot affect data serialization

## Standard Jackson Annotations

### @JsonProperty - Customize Field Names

Renames a Java field to a different name in JSON.

**Use case**: API uses `order_id` but your Java field is named `orderId`

```java
public class Order {
    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("customer_id")
    private String customerId;
}
```

**Serialization**:
```
Java:   Order(orderId="ORD-001", customerId="CUST-123")
JSON:   {"order_id": "ORD-001", "customer_id": "CUST-123"}
```

**Deserialization**:
```
JSON:   {"order_id": "ORD-001", "customer_id": "CUST-123"}
Java:   Order(orderId="ORD-001", customerId="CUST-123")
```

### @JsonIgnore - Exclude Fields

Prevents a field from being serialized/deserialized.

**Use case**: Internal ID that shouldn't be sent over the network, password fields

```java
public class User {
    private String username;

    @JsonIgnore
    private String internalDatabaseId;

    @JsonIgnore
    private String passwordHash;
}
```

**Serialization**:
```
Java:   User(username="alice", internalDatabaseId="db-123", passwordHash="hash...")
JSON:   {"username": "alice"}
```

**Deserialization**:
```
JSON:           {"username": "alice"}
Java:           User(username="alice", internalDatabaseId=null, passwordHash=null)
                (ignored fields are not populated from JSON)
```

### @JsonDeserialize - Custom Deserialization

Specify a custom deserializer for complex types.

**Use case**: Custom date parsing, domain value objects

```java
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class Event {
    private String name;

    @JsonDeserialize(using = CustomDateDeserializer.class)
    private LocalDateTime timestamp;
}

// Custom deserializer
public class CustomDateDeserializer extends StdDeserializer<LocalDateTime> {
    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String dateStr = p.getText();
        // Parse custom date format
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
```

**Deserialization**:
```
JSON:   {"name": "release", "timestamp": "2024-12-31 10:30:00"}
Java:   Event(name="release", timestamp=LocalDateTime(2024, 12, 31, 10, 30))
        (custom deserializer parsed the date)
```

### @JsonSerialize - Custom Serialization

Specify a custom serializer for complex types.

**Use case**: Custom date formatting, sensitive data redaction

```java
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class User {
    private String email;

    @JsonSerialize(using = SensitiveStringSerializer.class)
    private String creditCardNumber;
}

// Custom serializer that redacts the card number
public class SensitiveStringSerializer extends StdSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (value != null && value.length() > 4) {
            String redacted = "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
            gen.writeString(redacted);
        } else {
            gen.writeString(value);
        }
    }
}
```

**Serialization**:
```
Java:   User(email="user@example.com", creditCardNumber="4111-1111-1111-1111")
JSON:   {"email": "user@example.com", "creditCardNumber": "**************1111"}
        (custom serializer redacted the card number)
```

### @JsonAlias - Multiple JSON Names

Accept multiple JSON field names during deserialization.

**Use case**: API versioning, backwards compatibility

```java
public class Order {
    @JsonProperty("order_id")
    @JsonAlias("orderId")  // Accept both "order_id" and "orderId" in JSON
    private String orderId;
}
```

**Deserialization** - accepts both formats:
```
JSON 1:  {"order_id": "ORD-001"}      ✅ Works
JSON 2:  {"orderId": "ORD-001"}       ✅ Works
```

## Real-World Example

Here's a complete example combining multiple annotations:

```java
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class OrderData {
    @JsonProperty("order_id")
    private String id;

    @JsonProperty("customer_id")
    private String customerId;

    private double totalPrice;

    @JsonIgnore
    private String internalId;  // Not sent in JSON

    @JsonIgnore
    private String auditLog;    // Internal tracking only

    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    // Jackson needs no-arg constructor
    public OrderData() {}

    public OrderData(String id, String customerId, double totalPrice, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public double getTotalPrice() { return totalPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getInternalId() { return internalId; }
    public String getAuditLog() { return auditLog; }

    // Setters...
}
```

**Usage with NatsPublisher**:

```java
@Singleton
public class OrderService {
    @Inject
    @NatsSubject("orders")
    NatsPublisher<OrderData> orderPublisher;

    public void publishOrder(OrderData order) throws PublishingException {
        // Jackson serializes with all annotations:
        // - "id" becomes "order_id"
        // - "customerId" becomes "customer_id"
        // - internalId and auditLog are excluded
        // - createdAt uses custom deserializer
        orderPublisher.publish(order);
    }
}
```

**JSON on Wire**:
```json
{
  "order_id": "ORD-001",
  "customer_id": "CUST-123",
  "totalPrice": 299.99,
  "createdAt": "2024-12-31 10:30:00"
}
```

Note: `internalId` and `auditLog` are NOT in JSON (excluded by @JsonIgnore)

## Complete Annotation Reference

| Annotation | Purpose | Serialization | Deserialization |
|---|---|---|---|
| `@JsonProperty("name")` | Customize JSON field name | Uses custom name | Looks for custom name |
| `@JsonIgnore` | Exclude from JSON | Excluded | Not populated |
| `@JsonProperty(access = JsonProperty.Access.READ_ONLY)` | Output only | Included | Ignored |
| `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` | Input only | Ignored | Populated |
| `@JsonDeserialize(using = X.class)` | Custom deserializer | — | Uses custom deserializer |
| `@JsonSerialize(using = X.class)` | Custom serializer | Uses custom serializer | — |
| `@JsonAlias({"name1", "name2"})` | Accept multiple names | — | Accepts aliases |
| `@JsonFormat(pattern = "...")` | Format dates/times | Uses pattern | Parses with pattern |
| `@JsonInclude(JsonInclude.Include.NON_NULL)` | Conditional inclusion | Includes non-nulls only | — |

## Important Notes

### 1. This Library Doesn't Break Annotations

The library **delegates to Jackson's ObjectMapper directly**. We don't:
- Parse or modify JSON ourselves
- Intercept or change annotation processing
- Add wrapper layers that interfere with Jackson

Result: **All Jackson annotations work exactly as documented by Jackson**

### 2. CloudEvents Binary-Mode is Transparent

The library uses CloudEvents binary-mode, which means:
- JSON body is pure Jackson serialization (annotations applied)
- CloudEvents headers are metadata only (separate from body)
- Headers cannot affect JSON structure or annotations
- No risk of "interference"

Result: **Annotations work transparently through CloudEvents wrapping**

### 3. No Special Library Annotations

We don't provide custom annotations like:
- ❌ `@NatsSerialize` - use Jackson annotations instead
- ❌ `@NatsProperty` - use Jackson's `@JsonProperty`
- ❌ `@NatsIgnore` - use Jackson's `@JsonIgnore`

Reason: Jackson annotations are standard, well-documented, and more powerful

### 4. Validation Still Happens

Type validation (`TypeValidator`) ensures types are Jackson-compatible:
- Must have no-arg constructor (for POJOs)
- Cannot be primitives or arrays
- Must be instantiable

But once validation passes, all Jackson annotations work without restriction.

## Troubleshooting

### Problem: Field not appearing in JSON

**Cause**: Field might be marked with `@JsonIgnore` or excluded by `@JsonInclude`

**Solution**:
```java
// Check if @JsonIgnore is present
@JsonIgnore
private String field;  // Won't appear in JSON

// Remove @JsonIgnore or add explicit @JsonProperty
@JsonProperty("field")
private String field;  // Will appear in JSON
```

### Problem: Field name in JSON doesn't match Java field

**Expected**: Java field `orderId` → JSON key `orderId` (default)

**If mismatch**: Check for `@JsonProperty` annotation

```java
@JsonProperty("order_id")
private String orderId;  // JSON key is "order_id", not "orderId"
```

### Problem: Deserialization fails for specific field

**Cause**: Field type not supported by Jackson, or custom format needed

**Solution**: Add custom deserializer

```java
@JsonDeserialize(using = CustomDeserializer.class)
private CustomType field;
```

### Problem: Circular reference during serialization

**Cause**: Object has reference back to parent or container

**Solution**: Mark circular reference fields with `@JsonIgnore`

```java
public class Order {
    private String orderId;

    @JsonIgnore
    private Customer customer;  // Don't serialize this to avoid cycles
}
```

## Further Reading

- [Jackson Annotations Documentation](https://github.com/FasterXML/jackson-annotations/wiki)
- [Jackson Data Binding Documentation](https://github.com/FasterXML/jackson-databind)
- [CloudEvents Java SDK](https://github.com/cloudevents/sdk-java)

## Summary

**Key Takeaway**: The library is transparent to Jackson annotations. Use standard Jackson annotations to customize JSON serialization/deserialization. All Jackson features work without modification or restriction.
