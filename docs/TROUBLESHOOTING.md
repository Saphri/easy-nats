# Error Troubleshooting Guide - Quarkus EasyNATS

This guide helps you diagnose and fix common errors when using Quarkus EasyNATS with typed messages.

---

## Error: "Primitive type 'int' is not supported"

### When You See This Error

```
IllegalArgumentException: Invalid type 'int' for NatsPublisher:
Primitive type 'int' is not supported. Wrap it in a POJO:
public class IntValue {
    public int value;
    public IntValue() {}
    public IntValue(int value) { this.value = value; }
}
```

### What It Means

You're trying to use a primitive type (`int`, `long`, `double`, `boolean`, etc.) directly with Quarkus EasyNATS. Primitives don't have constructors, so Jackson can't deserialize them.

### Root Causes

1. **Direct publisher type parameter**: `NatsPublisher<int>` (won't compile)
2. **Publisher injected with wrong type**: Type extracted from generic parameter is primitive
3. **Subscriber method parameter**: `@NatsSubscriber public void handle(int value) { }`

### How to Fix It

**Option 1: Create a wrapper POJO (recommended)**

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
```

Then use it:

```java
// Publishing
@Dependent
public class NumberService {
    private final NatsPublisher<IntValue> publisher;

    public NumberService(@NatsSubject("numbers") NatsPublisher<IntValue> pub) {
        this.publisher = pub;
    }

    public void publishNumber(int value) throws Exception {
        publisher.publish(new IntValue(value));
    }
}

// Subscribing
@NatsSubscriber("numbers")
public void handleNumber(IntValue container) {
    int value = container.getValue();
    // Process the number
}
```

**Option 2: Use a composite POJO with other fields**

If the primitive is part of a larger message:

```java
public class OrderUpdate {
    private String orderId;
    private int quantity;  // Primitive inside POJO is fine
    private BigDecimal price;

    public OrderUpdate() {}

    public OrderUpdate(String orderId, int quantity, BigDecimal price) {
        this.orderId = orderId;
        this.quantity = quantity;
        this.price = price;
    }

    // Getters and setters
}

// Use it
NatsPublisher<OrderUpdate> publisher;
publisher.publish(new OrderUpdate("ORD-001", 5, new BigDecimal("99.99")));
```

**Option 3: Use Integer (wrapper class) instead of int**

```java
public class OrderQuantity {
    private Integer quantity;  // Integer, not int

    public OrderQuantity() {}

    public OrderQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }
}

// Use it
NatsPublisher<OrderQuantity> publisher;
publisher.publish(new OrderQuantity(5));
```

---

## Error: "Array type [...] is not supported"

### When You See This Error

```
IllegalArgumentException: Array type '[I' is not supported. Wrap it in a POJO:
public class IntList {
    public int[] items;
    public IntList() {}
    public IntList(int[] items) { this.items = items; }
}
```

### What It Means

You're trying to use an array type (`int[]`, `String[]`, etc.) directly. Arrays don't have no-arg constructors, so Jackson can't deserialize them.

### Root Causes

1. **Subscriber parameter is array**: `@NatsSubscriber public void handle(String[] tags) { }`
2. **Publisher type parameter is array**: Generic type resolved to array type

### How to Fix It

**Option 1: Use a wrapper POJO (recommended)**

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
```

Then use it:

```java
// Publishing
public void publishTags(String... tags) throws Exception {
    NatsPublisher<StringList> publisher;
    publisher.publish(new StringList(tags));
}

// Subscribing
@NatsSubscriber("tags")
public void handleTags(StringList container) {
    String[] tags = container.getItems();
    for (String tag : tags) {
        System.out.println(tag);
    }
}
```

**Option 2: Use a List instead of array**

Lists are supported directly:

```java
// Publishing
public void publishTags(String... tags) throws Exception {
    NatsPublisher<List<String>> publisher;
    publisher.publish(Arrays.asList(tags));
}

// Subscribing
@NatsSubscriber("tags")
public void handleTags(List<String> tags) {
    for (String tag : tags) {
        System.out.println(tag);
    }
}
```

This is often cleaner than using arrays.

---

## Error: "requires a no-arg constructor"

### When You See This Error

```
IllegalArgumentException: Type 'OrderRequest' requires a no-arg constructor for Jackson deserialization.
Add a no-arg constructor or use @JsonDeserialize with a custom deserializer.
```

### What It Means

Your class only has constructors that require parameters. Jackson can't instantiate the class without a no-arg constructor.

### Root Causes

1. **Class has only parameterized constructor**: `public Order(String id, String customer) { }`
2. **No default constructor defined**

### How to Fix It

**Option 1: Add a no-arg constructor (recommended)**

```java
// ❌ Before (won't work)
public class Order {
    private String id;
    private String customer;

    public Order(String id, String customer) {
        this.id = id;
        this.customer = customer;
    }

    // Getters
}

// ✅ After (add no-arg constructor)
public class Order {
    private String id;
    private String customer;

    public Order() {
        // No-arg constructor for Jackson
    }

    public Order(String id, String customer) {
        this.id = id;
        this.customer = customer;
    }

    // Getters
}
```

**Option 2: Convert to Java Record**

Records (Java 14+) automatically support no-arg constructors:

```java
public record Order(
    String id,
    String customer
) {
    // Records automatically generate:
    // - no-arg constructor (Java 17+)
    // - getters (id(), customer())
    // - equals(), hashCode(), toString()
}

// Use it - Jackson handles everything
NatsPublisher<Order> publisher;
publisher.publish(new Order("ORD-001", "CUST-001"));
```

**Option 3: Create a wrapper with custom deserializer**

If you can't modify the original class:

```java
public class OrderRequest {
    // Only has parameterized constructor - can't change it
    public OrderRequest(String id, String customer) { ... }
}

public class OrderMessage {
    private OrderRequest request;

    public OrderMessage() {
        // No-arg for Jackson
    }

    public OrderMessage(OrderRequest request) {
        this.request = request;
    }

    public OrderRequest getRequest() {
        return request;
    }
}

// Use it
NatsPublisher<OrderMessage> publisher;
publisher.publish(new OrderMessage(orderRequest));
```

---

## Error: "Failed to deserialize: Unrecognized field"

### When You See This Error

```
Failed to deserialize to type 'OrderData':
  Root cause: Unrecognized field "order_id" (not marked as ignorable)
  Raw payload: {"order_id": "ORD-001", "customerId": "CUST-001"}
```

### What It Means

The JSON received from NATS has a field that your class doesn't recognize. This happens when:
1. Field names in JSON don't match Java field names
2. JSON has extra fields your class doesn't have
3. Field was renamed or removed

### Root Causes

1. **JSON field names don't match Java field names**: JSON has `order_id` but Java class expects `orderId`
2. **JSON has extra fields**: Old code is sending more fields than your new class expects
3. **Field renaming without annotation**: Someone renamed a field without updating the mapping

### How to Fix It

**Option 1: Use @JsonProperty to match JSON field names**

```java
// ❌ Before (mismatch)
public class OrderData {
    private String orderId;      // Java: orderId
    // But JSON has: order_id
}

// ✅ After (use @JsonProperty)
public class OrderData {
    @JsonProperty("order_id")
    private String orderId;

    private String customerId;

    // Constructors, getters, setters
}
```

Now JSON with `"order_id"` deserializes to the `orderId` field.

**Option 2: Allow extra JSON fields**

Use `@JsonIgnoreProperties` to ignore unknown fields:

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderData {
    private String orderId;
    private String customerId;
    // If JSON has extra fields, they're silently ignored
}
```

**Option 3: Rename your class field to match JSON**

If the JSON is correct and your Java field is wrong:

```java
// ❌ Before
public class OrderData {
    private String orderId;      // Doesn't match JSON field "order_id"
}

// ✅ After
public class OrderData {
    private String order_id;     // Now matches JSON

    // Or use getter that camelCases it
    public String getOrderId() {
        return order_id;
    }
}
```

**Option 4: Use @JsonAnySetter for flexible fields**

If JSON might have variable fields:

```java
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class OrderData {
    private String orderId;
    private Map<String, Object> unknownFields = new HashMap<>();

    @JsonAnySetter
    public void set(String key, Object value) {
        unknownFields.put(key, value);
    }

    // Now unknown fields are stored in unknownFields instead of causing errors
}
```

---

## Error: "Failed to deserialize: Unexpected character"

### When You See This Error

```
Failed to deserialize to type 'OrderData':
  Root cause: Unexpected character ('"' (code 34)): expected valid JSON number
  Raw payload: {"quantity": "five"}
```

### What It Means

The JSON value doesn't match the Java field type. For example, JSON has a string `"five"` but your field is `int quantity`.

### Root Causes

1. **Type mismatch**: JSON has string but field expects number
2. **Malformed JSON**: JSON syntax error
3. **Wrong data sent**: Upstream service sent wrong type

### How to Fix It

**Option 1: Fix the upstream data**

Ensure data is sent with correct types:

```java
// ❌ Wrong: quantity as string
json: {"quantity": "five", ...}

// ✅ Correct: quantity as number
json: {"quantity": 5, ...}
```

**Option 2: Use a custom deserializer**

If you need to accept multiple formats:

```java
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.DeserializationContext;

public class OrderData {
    @JsonDeserialize(using = QuantityDeserializer.class)
    private int quantity;

    // Constructors, getters, setters
}

public class QuantityDeserializer extends StdDeserializer<Integer> {
    public QuantityDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String text = p.getText();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new JsonMappingException(p, "Expected number, got: " + text);
        }
    }
}
```

**Option 3: Accept String and convert**

```java
public class OrderData {
    private String quantityStr;

    public int getQuantity() {
        return Integer.parseInt(quantityStr);
    }
}
```

---

## Error: "Failed to serialize: Infinite recursion detected"

### When You See This Error

```
Failed to serialize type 'OrderData':
  Root cause: Direct self-reference leading to cycle
```

### What It Means

Your class has a field that references back to itself (directly or indirectly), creating an infinite loop during serialization.

### Common Causes

1. **Self-referential field**: `class Node { Node parent; }`
2. **Circular reference**: `Order` → `Customer` → `Order`
3. **Bidirectional relationship**: Both sides reference each other

### How to Fix It

**Option 1: Use @JsonIgnore on back-reference**

```java
// ❌ Before (circular)
public class Order {
    private String orderId;
    private Customer customer;
}

public class Customer {
    private String customerId;
    private List<Order> orders;  // Back-reference to orders
}

// ✅ After (break cycle with @JsonIgnore)
public class Order {
    private String orderId;
    @JsonIgnore
    private Customer customer;  // Don't serialize the customer
}

public class Customer {
    private String customerId;
    private List<Order> orders;  // This is fine
}
```

**Option 2: Use @JsonManagedReference and @JsonBackReference**

```java
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

public class Customer {
    private String customerId;

    @JsonManagedReference
    private List<Order> orders;
}

public class Order {
    private String orderId;

    @JsonBackReference
    private Customer customer;  // Won't be serialized
}
```

**Option 3: Custom serializer to control recursion**

```java
@JsonSerialize(using = CustomerSerializer.class)
public class Customer {
    private String customerId;
    private List<Order> orders;
}

public class CustomerSerializer extends StdSerializer<Customer> {
    public CustomerSerializer() {
        super(Customer.class);
    }

    @Override
    public void serialize(Customer value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("customerId", value.getCustomerId());
        // Don't serialize orders to avoid cycle
        gen.writeEndObject();
    }
}
```

---

## Error: "Type has unresolvable generic parameter"

### When You See This Error

```
Type 'java.util.List' has unresolvable generic parameter.
You must use a concrete type like List<String> or List<OrderData>.
```

### What It Means

You're using a generic type (like `List`) without specifying the type parameter. Jackson can't know what type to deserialize into.

### Root Causes

1. **Raw generic type**: `List` instead of `List<T>`
2. **Type erasure**: Generic information lost at runtime

### How to Fix It

**Option 1: Use concrete generic type**

```java
// ❌ Wrong
NatsPublisher<List> publisher;  // Raw type, type parameter unknown

@NatsSubscriber("orders")
public void handleOrders(List orders) {  // What type?
}

// ✅ Correct
NatsPublisher<List<OrderData>> publisher;  // Concrete type

@NatsSubscriber("orders")
public void handleOrders(List<OrderData> orders) {  // Type known
}
```

**Option 2: Create a wrapper class for complex generics**

```java
// ✅ If you need a concrete type at runtime
public class OrderList extends ArrayList<OrderData> {
}

// Use it
NatsPublisher<OrderList> publisher;
publisher.publish(orderList);
```

**Option 3: Use a DTO class**

```java
public class OrderBatch {
    private List<OrderData> orders;

    public OrderBatch() {}

    public OrderBatch(List<OrderData> orders) {
        this.orders = orders;
    }

    public List<OrderData> getOrders() {
        return orders;
    }
}

// Use it
NatsPublisher<OrderBatch> publisher;
publisher.publish(new OrderBatch(orders));
```

---

## Error: "Failed to serialize: No serializer found"

### When You See This Error

```
Failed to serialize type 'OrderData':
  Root cause: No serializer found for class java.util.Date
```

### What It Means

You're using a field type that Jackson doesn't know how to serialize. This is usually `java.util.Date`, `java.sql.Timestamp`, or custom types.

### Root Causes

1. **Using java.util.Date**: Use `java.time.LocalDate` instead
2. **Custom type without serializer**: Custom class that Jackson doesn't understand
3. **Missing dependency**: Jackson module for specific type not on classpath

### How to Fix It

**Option 1: Use java.time types instead of java.util.Date**

```java
// ❌ Before (java.util.Date)
public class Order {
    private Date createdAt;
    private Date updatedAt;
}

// ✅ After (java.time)
public class Order {
    private LocalDate createdAt;
    private LocalDateTime updatedAt;
}
```

Jackson has built-in support for `java.time` types.

**Option 2: Configure Jackson to handle java.util.Date**

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

ObjectMapper mapper = new ObjectMapper();
mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
```

**Option 3: Use @JsonSerialize for custom format**

```java
public class Order {
    @JsonSerialize(using = DateSerializer.class)
    private Date createdAt;
}

public class DateSerializer extends StdSerializer<Date> {
    public DateSerializer() {
        super(Date.class);
    }

    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(new SimpleDateFormat("yyyy-MM-dd").format(value));
    }
}
```

**Option 4: Create custom serializer for custom type**

```java
public class CustomTypeField {
    private MyCustomType value;
}

@JsonSerialize(using = MyCustomTypeSerializer.class)
public class MyCustomType {
    // Custom fields
}

public class MyCustomTypeSerializer extends StdSerializer<MyCustomType> {
    public MyCustomTypeSerializer() {
        super(MyCustomType.class);
    }

    @Override
    public void serialize(MyCustomType value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(value.toString());  // Or custom serialization
    }
}
```

---

## Quick Reference Table

| Error Message | Likely Cause | Quick Fix |
|---------------|--------------|-----------|
| Primitive type 'int' not supported | Using `int` directly | Wrap in POJO: `IntValue` |
| Array type '[I' not supported | Using `int[]` directly | Wrap in POJO: `IntList` |
| requires a no-arg constructor | No default constructor | Add `public ClassName() {}` |
| Unrecognized field "order_id" | JSON field name mismatch | Use `@JsonProperty("order_id")` |
| Unexpected character... expected valid JSON | Type mismatch in JSON | Verify JSON data types |
| Infinite recursion detected | Circular reference | Use `@JsonIgnore` on back-reference |
| No serializer found for... | Unsupported field type | Use `@JsonSerialize(using = ...)` |

---

## When to Ask for Help

If you see an error that's not covered here:

1. **Read the error message fully**: It usually tells you exactly what's wrong
2. **Check the Raw Payload**: The actual JSON might reveal the issue
3. **Consult JACKSON_COMPATIBILITY_GUIDE.md**: Verify your types are supported
4. **Try WRAPPER_PATTERN.md**: See if wrapping helps
- **Enable Debug Logging**: Set log level to DEBUG for more details

Example debug logging:

```properties
# application.properties
quarkus.log.level=DEBUG
quarkus.log.category."org.mjelle.quarkus.easynats".level=DEBUG
```

---

## Error: "JetStreamApiException: nak received for a message that is not outstanding"

### When You See This Error

You are using explicit acknowledgment with `NatsMessage<T>` and call `msg.nak()` or `msg.nakWithDelay()`, but the call fails with a `JetStreamApiException`.

### What It Means

You are trying to negatively acknowledge a message, but the Nats JetStream consumer is not configured to handle explicit acknowledgments. The consumer's `AckPolicy` is likely set to `all` or `none` instead of `explicit`.

### Root Causes

1.  **Incorrect Consumer Configuration**: The durable consumer was created without setting the acknowledgment policy to explicit.
2.  **Using `nak()` with an Ephemeral Consumer**: Ephemeral consumers do not support explicit `nak`.

### How to Fix It

Ensure your durable consumer is created with `--ack explicit`.

**1. Delete the old consumer (if it exists):**
```bash
nats consumer rm YOUR_STREAM YOUR_CONSUMER
```

**2. Re-create the consumer with the correct ack policy:**
```bash
nats consumer add YOUR_STREAM YOUR_CONSUMER --ack explicit
```

**Example:**
```bash
# For a stream named "ORDERS" and a consumer named "order-processor"
nats consumer add ORDERS order-processor --ack explicit
```

By setting the acknowledgment policy to `explicit`, you tell NATS JetStream that your application will be responsible for manually acknowledging (`ack`) or negatively acknowledging (`nak`) each message.

---

## See Also

- [JACKSON_COMPATIBILITY_GUIDE.md](./JACKSON_COMPATIBILITY_GUIDE.md) - Which types are supported
- [WRAPPER_PATTERN.md](./WRAPPER_PATTERN.md) - How to wrap unsupported types
- [JACKSON_ANNOTATIONS_GUIDE.md](./JACKSON_ANNOTATIONS_GUIDE.md) - Using Jackson annotations

---

## Health Probe Behavior: Startup Probe vs. Readiness/Liveness

### Observation: Startup Probe (`/q/health/started`) Stays "UP" After NATS Disconnection

You may notice that after your application has started successfully, the startup probe continues to report `"status": "UP"` even if the connection to the NATS server is lost.

### Why This Happens (Intended Behavior)

This is the **correct and intended behavior** for a startup probe in a containerized environment like Kubernetes.

- **Purpose of the Startup Probe**: The startup probe's only job is to tell the orchestrator that the application has successfully initialized one time. Once it reports "UP", it has fulfilled its purpose.
- **Latching Mechanism**: The startup probe is intentionally designed to "latch" in the "UP" state. It will not revert to "DOWN" after the initial successful connection. This prevents the orchestrator from killing the application due to transient network issues or a temporary NATS server outage that occurs *after* the application was already running correctly.

### Which Probe to Use for Connection Status

- **For real-time connection status**, use the **readiness probe** (`/q/health/ready`) or the **liveness probe** (`/q/health/live`).
- **Readiness Probe**: This probe will report "DOWN" if the NATS connection is lost, signaling to the orchestrator to temporarily stop sending traffic to the application.
- **Liveness Probe**: This probe will also report "DOWN" on connection loss. If it remains "DOWN" for a configured period, the orchestrator will restart the application, assuming it's in an unrecoverable state.

| Probe | Endpoint | Behavior |
|---|---|---|
| **Startup** | `/q/health/started` | Reports "UP" once after the first successful connection, then stays "UP". |
| **Readiness** | `/q/health/ready` | Reports the real-time connection status. |
| **Liveness** | `/q/health/live` | Reports the real-time connection status. |
