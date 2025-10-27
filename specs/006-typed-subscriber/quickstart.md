# Quickstart: Typed Subscriber with @NatsSubscriber

**Target Audience**: Extension developers and application developers
**Prerequisites**: Quarkus 3.27.0, Java 21, NATS server with JetStream enabled

---

## Feature Overview

The typed subscriber feature allows you to receive CloudEvents-wrapped messages with automatic deserialization into typed Java objects (POJOs, records, generics).

### Before (004-nats-subscriber-mvp - String only)
```java
@NatsSubscriber(subject = "orders.created")
public void handleOrder(String rawPayload) {
    // Manual JSON parsing required
    Order order = parseJson(rawPayload, Order.class);
    processOrder(order);
}
```

### After (006-typed-subscriber - Typed objects)
```java
@NatsSubscriber(subject = "orders.created")
public void handleOrder(Order order) {
    // Automatic deserialization from CloudEvent data field
    processOrder(order);
}
```

---

## Usage Example

### 1. Define Your Domain Model

```java
// POJO with no-arg constructor
class Order {
    String orderId;
    String customerId;
    List<OrderItem> items;
    BigDecimal totalPrice;

    // Getters/setters or @JsonProperty annotations
}

class OrderItem {
    String sku;
    int quantity;
}
```

Or use a **Java 21 Record** (simpler):

```java
record Order(
    String orderId,
    String customerId,
    List<OrderItem> items,
    BigDecimal totalPrice
) {}

record OrderItem(String sku, int quantity) {}
```

### 2. Create Subscriber Bean

```java
import io.quarkus.arc.Arc;
import io.nats.ext.subscriber.NatsSubscriber;

@ApplicationScoped
public class OrderService {

    @NatsSubscriber(subject = "orders.created")
    public void handleOrderCreated(Order order) {
        System.out.println("Order received: " + order.orderId());
        // Typed object is automatically deserialized from CloudEvent data field
        processOrder(order);
    }

    private void processOrder(Order order) {
        // Your business logic here
    }
}
```

### 3. Publish CloudEvents (Binary-Mode)

The subscriber expects **CloudEvents 1.0 binary-mode format only**:
- Attributes (ce-specversion, ce-type, ce-source, ce-id) in NATS message headers with `ce-` prefix
- Event data in message payload (JSON string)
- Structured-mode (entire CloudEvent in payload) is NOT supported

Use the `@NatsPublisher` from the extension to send:

```java
@Inject
@NatsSubject("orders.created")
NatsPublisher publisher;

// In your method:
Order newOrder = new Order(...);
publisher.publish(newOrder);  // Automatically wraps in CloudEvents
```

Or publish CloudEvents manually (binary-mode):

```java
// Binary-mode CloudEvents: Attributes in headers, data in payload
Map<String, String> headers = new HashMap<>();

// Required CloudEvents attributes (in header with ce- prefix)
headers.put("ce-specversion", "1.0");
headers.put("ce-type", "com.example.OrderCreated");
headers.put("ce-source", "/ordering/api");
headers.put("ce-id", "order-" + UUID.randomUUID());
headers.put("ce-time", Instant.now().toString());
headers.put("ce-datacontenttype", "application/json");

// Event data in payload (JSON string)
String orderJson = objectMapper.writeValueAsString(order);
byte[] payload = orderJson.getBytes(StandardCharsets.UTF_8);

// Publish with binary-mode CloudEvents format
natsConnection.jetStream().publish("orders.created", headers, payload);

// Subscriber receives the Order object automatically deserialized from payload
```

### 4. Run Your Application

Configuration in `application.properties`:
```properties
nats.servers=nats://localhost:4222
nats.username=admin
nats.password=secret
```

Start your Quarkus app:
```bash
./mvnw quarkus:dev
```

---

## Supported Parameter Types

The subscriber method parameter can be any type that `TypedPayloadEncoder` supports when sending (true bidirectional type support).

### Primitive Wrappers (UTF-8 encoded)
```java
@NatsSubscriber(subject = "metrics.count")
public void handleCount(Integer count) {
    System.out.println("Count: " + count);
    // Receives: Integer("42" → 42)
}
```

### String (direct UTF-8)
```java
@NatsSubscriber(subject = "events.message")
public void handleMessage(String message) {
    System.out.println("Message: " + message);
    // Receives: String("hello world")
}
```

### Byte Arrays (base64-encoded)
```java
@NatsSubscriber(subject = "data.binary")
public void handleBinary(byte[] data) {
    System.out.println("Received " + data.length + " bytes");
    // Receives: byte[] ({1, 2, 3} from "AQID" base64)
}
```

### Primitive Arrays (space-separated)
```java
@NatsSubscriber(subject = "analytics.numbers")
public void handleNumbers(int[] numbers) {
    System.out.println("Sum: " + Arrays.stream(numbers).sum());
    // Receives: int[] ({1, 2, 3} from "1 2 3")
}

@NatsSubscriber(subject = "flags.booleans")
public void handleFlags(boolean[] flags) {
    // Receives: boolean[] from space-separated values
}
```

### String Arrays (comma-separated)
```java
@NatsSubscriber(subject = "tags.list")
public void handleTags(String[] tags) {
    System.out.println("Tags: " + String.join(", ", tags));
    // Receives: String[] ({"a", "b", "c"} from "a,b,c")
}
```

### Complex Types - POJO
```java
class Order {
    String orderId;
    String customerId;
    List<OrderItem> items;
    BigDecimal totalPrice;
}

@NatsSubscriber(subject = "orders.created")
public void handleOrder(Order order) {
    System.out.println("Order: " + order.orderId);
    // Receives: Order instance from JSON deserialization
}
```

### Complex Types - Records (Java 21+)
```java
record User(String userId, String name, String email) {}
record Product(String sku, String name, BigDecimal price) {}

@NatsSubscriber(subject = "users.registered")
public void handleUser(User user) {
    System.out.println("User: " + user.name());
}

@NatsSubscriber(subject = "products.listed")
public void handleProduct(Product product) {
    System.out.println("Product: " + product.name());
}
```

### Complex Types - Collections/Generics
```java
@NatsSubscriber(subject = "batch.items")
public void handleBatch(List<Item> items) {
    items.forEach(item -> System.out.println(item.name()));
    // Receives: List<Item> from JSON array deserialization
}

@NatsSubscriber(subject = "mapping.data")
public void handleMapping(Map<String, Integer> mapping) {
    mapping.forEach((key, value) -> System.out.println(key + "=" + value));
    // Receives: Map<String, Integer> from JSON object
}

@NatsSubscriber(subject = "wrapped.events")
public void handleWrapped(Wrapper<Order> wrapper) {
    Order order = wrapper.data();
    // Receives: Wrapper instance with generic Order data
}
```

### Type Matrix

| Type Category | Example | Encoding | Decoding |
|---|---|---|---|
| Primitive Wrapper | Integer, Long, Boolean | UTF-8 string | Parse from UTF-8 |
| String | String | UTF-8 string | Direct UTF-8 |
| Byte Types | byte[], Byte | Base64 string | Base64 decode |
| Primitive Array | int[], double[] | Space-separated | Split + parse |
| String Array | String[] | Comma-separated | Split on comma |
| POJO | Order, User | JSON | Jackson deserialize |
| Record | record User(...) | JSON | Jackson deserialize |
| Generic | List<T>, Map<K,V> | JSON | Jackson + TypeReference |

### What's NOT Supported
- ❌ Raw primitives (int, long, boolean) - use wrapper types (Integer, Long, Boolean)
- ❌ Interfaces without implementation
- ❌ Abstract classes without @JsonDeserialize
- ❌ Structured-mode CloudEvents (binary-mode only)

---

## Error Handling

Errors are handled implicitly. The framework automatically:

1. **Nacks invalid CloudEvents** (missing ce-* headers)
   - Allows redelivery
   - Logs error at ERROR level
   - Continues processing next message

2. **Nacks deserialization failures** (JSON doesn't match type)
   - Allows redelivery
   - Logs error with type mismatch details
   - Continues processing

3. **Nacks method exceptions** (your code throws exception)
   - Allows redelivery
   - Logs exception stack trace
   - Continues processing

**Example**: If your method throws an exception, the message is nacked:

```java
@NatsSubscriber(subject = "orders.created")
public void handleOrder(Order order) {
    if (order.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Invalid order price");
        // Message is nacked on exception, can be redelivered
    }
    processOrder(order);  // Acked on success
}
```

---

## Build-Time Validation

The Quarkus build fails if your subscriber method has invalid types:

### Error: Parameter Type Not Jackson-Deserializable

```
Build failure:
  Method handleOrder(MyInterface) parameter MyInterface is not Jackson-deserializable.
  Ensure it has a no-arg constructor or @JsonCreator annotation.
```

**Fix**: Make sure your POJO has:
- No-arg constructor (or @JsonCreator-annotated constructor)
- Setters/field annotations for JSON property mapping

```java
class Order {
    @JsonProperty("order_id")
    String orderId;

    public Order() {}  // No-arg constructor required
}
```

### Error: String Parameter (014 Only)

```
Build failure:
  Method handleOrder(String) uses String parameter.
  String-only subscribers are in 004-nats-subscriber-mvp.
  Use 006-typed-subscriber for typed objects.
```

**Fix**: Use 004 if you need String payloads, or upgrade to typed objects.

---

## Testing Your Subscriber

### Unit Test with Mock

```java
@QuarkusTest
class OrderServiceTest {

    @Inject
    OrderService service;

    @Test
    void testOrderProcessing() {
        Order order = new Order("ORD-001", "CUST-001", List.of(...), new BigDecimal("99.99"));

        // Call method directly (not recommended for production testing)
        service.handleOrderCreated(order);

        // Verify results
        // ...
    }
}
```

### Integration Test with Real NATS

```java
@QuarkusIntegrationTest
class OrderServiceIT {

    @Inject
    NatsPublisher publisher;

    @Test
    void testOrderFromNATS() throws Exception {
        Order expectedOrder = new Order("ORD-001", "CUST-001", ...);

        // Publish CloudEvent via NATS
        publisher.publish(expectedOrder);

        // Wait for subscriber to process (using Awaitility)
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                // Verify business logic results
                assertThat(isOrderProcessed("ORD-001")).isTrue();
            });
    }
}
```

---

## Customizing CloudEvent Handling

### Custom ObjectMapper Configuration

If you need custom Jackson configuration (custom deserializers, annotations):

```java
@Singleton
public class ObjectMapperProducer {

    @Produces
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

The subscriber framework will automatically use your custom ObjectMapper.

---

## Migration from 004 to 006

### Before (004: String-only)
```java
@NatsSubscriber(subject = "orders.created")
public void handleOrder(String json) {
    Order order = objectMapper.readValue(json, Order.class);
    processOrder(order);
}
```

### After (006: Typed)
```java
@NatsSubscriber(subject = "orders.created")
public void handleOrder(Order order) {
    processOrder(order);
}
```

**What changed**:
- ✅ Remove manual JSON parsing
- ✅ Jackson deserialization now automatic
- ✅ Build-time type validation ensures correctness
- ✅ CloudEvent unwrapping is transparent
- ✅ Same ack/nak semantics (inherited from 004)

---

## Troubleshooting

### "Message not arriving"

1. **Check CloudEvent binary-mode format**: Verify `ce-specversion`, `ce-type`, `ce-source`, `ce-id` headers are present and not in payload
2. **Check subject**: Subscriber subject must match published subject (supports wildcards from NATS)
3. **Check connection**: Verify NATS server is running and credentials are correct
4. **Verify binary-mode**: Confirm message uses binary-mode (attributes in headers, data in payload), not structured-mode (entire CloudEvent in payload)

### "Type mismatch error"

1. **Verify JSON structure**: Does the CloudEvent data field match your Java type?
2. **Check Jackson configuration**: Are all fields properly annotated (@JsonProperty)?
3. **Check Jackson modules**: Time fields may need JavaTimeModule

### "Message is being redelivered indefinitely"

1. **Check subscriber code**: Does method throw exception? Exception causes nack → redelivery
2. **Check deserialization**: Is JSON valid for the type? Fix the CloudEvent publisher
3. **Configure retry policy**: Set consumer-level max retries in NATS (if supported in future MVP)

---

## Performance Considerations

- **Deserialization latency**: ~1-5ms per message (typical Jackson overhead)
- **CloudEvent parsing**: <1ms overhead per message
- **Memory**: Type information resolved at build-time (zero runtime reflection)
- **Throughput**: Determined by NATS JetStream push consumer (typically 10k+ msg/sec)

---

## What's NOT Supported (Yet)

- ❌ Manual ack/nak control (deferred to future MVP with ConsumerContext)
- ❌ Durable consumers (ephemeral only, per 004)
- ❌ Consumer groups / queue pull model
- ❌ Custom error handlers per method
- ❌ Binary event data (JSON only)

---

## Next Steps

1. **Explore 004-nats-subscriber-mvp** for String-only subscribers
2. **Explore 005-transparent-cloudevents** for CloudEvent publishing patterns
3. **Check integration-tests** for more examples
4. **Review NATS documentation** for JetStream consumer configuration options
