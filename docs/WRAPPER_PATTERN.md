# Wrapping Unsupported Types - The Wrapper Pattern

When you need to use a type that Quarkus EasyNATS doesn't support directly (primitives, arrays, or types without no-arg constructors), wrap it in a POJO. This guide shows how.

## When Do You Need Wrapping?

You need to wrap a type when:

1. **Primitive Types**: You want to send `int`, `long`, `double`, `boolean`, etc.
2. **Array Types**: You want to send `int[]`, `String[]`, or other arrays
3. **Types Without No-Arg Constructor**: You have a class that only has parameterized constructors
4. **Type Parameters**: You need a concrete wrapper for a generic type

---

## Example 1: Wrapping Primitive int

Primitives like `int` can't be published directly because they don't have constructors (they're language keywords, not classes).

### Problem: Direct Use (Won't Work)

```java
// ❌ This won't compile - primitive types can't be type parameters
NatsPublisher<int> publisher;

@NatsSubscriber("numbers")
public void handleNumber(int value) {  // ❌ Won't work
}
```

### Solution: Create IntValue Wrapper

```java
/**
 * Wrapper for primitive int values.
 *
 * Use when you need to publish/subscribe to individual integers
 * in a type-safe way.
 */
public class IntValue {
    private int value;

    /**
     * No-arg constructor required for Jackson deserialization.
     */
    public IntValue() {
    }

    /**
     * Convenience constructor for easy creation.
     */
    public IntValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "IntValue{" + "value=" + value + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntValue intValue = (IntValue) o;
        return value == intValue.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }
}
```

### Using the Wrapper

```java
// Publisher
@Dependent
public class NumberService {
    private final NatsPublisher<IntValue> publisher;

    public NumberService(@NatsSubject("numbers") NatsPublisher<IntValue> publisher) {
        this.publisher = publisher;
    }

    public void publishNumber(int value) throws Exception {
        publisher.publish(new IntValue(value));
    }
}

// Subscriber
@Singleton
public class NumberListener {
    @NatsSubscriber("numbers")
    public void handleNumber(IntValue container) {
        int value = container.getValue();
        System.out.println("Received number: " + value);
    }
}
```

### Generated JSON

When you publish `new IntValue(42)`, the JSON looks like:

```json
{
  "value": 42
}
```

When the subscriber receives this JSON, Jackson automatically deserializes it to `new IntValue(42)`.

---

## Example 2: Wrapping Array String[]

Arrays are not supported directly because they don't have no-arg constructors (they're created with `new String[size]`, not `new String[]()` ).

### Problem: Direct Use (Won't Work)

```java
// ❌ Won't work - array types not supported
NatsPublisher<String[]> publisher;

@NatsSubscriber("tags")
public void handleTags(String[] tags) {  // ❌ Build-time error
}
```

### Solution: Create StringList Wrapper

```java
/**
 * Wrapper for String arrays.
 *
 * Use when you need to publish/subscribe to collections of strings
 * in a structured way.
 */
public class StringList {
    private String[] items;

    /**
     * No-arg constructor required for Jackson deserialization.
     */
    public StringList() {
    }

    /**
     * Convenience constructor for easy creation from varargs.
     */
    public StringList(String... items) {
        this.items = items;
    }

    /**
     * Copy constructor for collections.
     */
    public StringList(List<String> items) {
        this.items = items.toArray(new String[0]);
    }

    public String[] getItems() {
        return items;
    }

    public void setItems(String[] items) {
        this.items = items;
    }

    public int size() {
        return items != null ? items.length : 0;
    }

    public String get(int index) {
        return items != null ? items[index] : null;
    }

    @Override
    public String toString() {
        return "StringList{" + "items=" + Arrays.toString(items) + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringList that = (StringList) o;
        return Arrays.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(items);
    }
}
```

### Using the Wrapper

```java
// Publisher
@Dependent
public class TagService {
    private final NatsPublisher<StringList> publisher;

    public TagService(@NatsSubject("tags") NatsPublisher<StringList> publisher) {
        this.publisher = publisher;
    }

    public void publishTags(String... tags) throws Exception {
        publisher.publish(new StringList(tags));
    }

    public void publishTagsFromList(List<String> tags) throws Exception {
        publisher.publish(new StringList(tags));
    }
}

// Subscriber
@Singleton
public class TagListener {
    @NatsSubscriber("tags")
    public void handleTags(StringList container) {
        String[] tags = container.getItems();
        System.out.println("Received " + tags.length + " tags: " + Arrays.toString(tags));
    }
}
```

### Generated JSON

When you publish `new StringList("urgent", "express", "tracked")`, the JSON looks like:

```json
{
  "items": ["urgent", "express", "tracked"]
}
```

---

## Example 3: Wrapping Type Without No-Arg Constructor

Sometimes you have a class that only has parameterized constructors. Create a wrapper POJO or add a no-arg constructor to the original class.

### Problem: Class Only Has Parameterized Constructor

```java
public class OrderRequest {
    private String orderId;
    private String customerId;

    // Only constructor - requires both parameters
    public OrderRequest(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }
}

// ❌ Won't work - no no-arg constructor
NatsPublisher<OrderRequest> publisher;
```

### Solution A: Add No-Arg Constructor (Preferred)

```java
public class OrderRequest {
    private String orderId;
    private String customerId;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public OrderRequest() {
    }

    /**
     * Constructor for convenience creation.
     */
    public OrderRequest(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    // Getters and setters
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
}

// ✅ Now works
NatsPublisher<OrderRequest> publisher;
```

### Solution B: Create a Wrapper (If You Can't Modify Original)

```java
/**
 * Wrapper for OrderRequest to support Jackson deserialization.
 *
 * Use when you cannot modify the original OrderRequest class.
 */
public class OrderRequestMessage {
    private OrderRequest request;

    public OrderRequestMessage() {
    }

    public OrderRequestMessage(OrderRequest request) {
        this.request = request;
    }

    public OrderRequest getRequest() {
        return request;
    }

    public void setRequest(OrderRequest request) {
        this.request = request;
    }
}

// Now use it:
public void publishOrder(OrderRequest order) throws Exception {
    NatsPublisher<OrderRequestMessage> publisher;
    publisher.publish(new OrderRequestMessage(order));
}
```

**Note**: This approach adds a wrapper layer. If possible, prefer Solution A (adding no-arg constructor directly).

---

## Creating Wrapper Types for Primitive Values

Use this template when wrapping any primitive type:

```java
/**
 * Wrapper for primitive {PRIMITIVE_TYPE} values.
 *
 * Jackson cannot serialize/deserialize primitive types directly because
 * they don't have constructors. Use this wrapper to transport {PRIMITIVE_TYPE}
 * values through Quarkus EasyNATS.
 */
public class {CAPITALIZED_TYPE}Value {
    private {PRIMITIVE_TYPE} value;

    /**
     * No-arg constructor required for Jackson deserialization.
     */
    public {CAPITALIZED_TYPE}Value() {
    }

    /**
     * Convenience constructor for easy creation.
     */
    public {CAPITALIZED_TYPE}Value({PRIMITIVE_TYPE} value) {
        this.value = value;
    }

    public {PRIMITIVE_TYPE} getValue() {
        return value;
    }

    public void setValue({PRIMITIVE_TYPE} value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        {CAPITALIZED_TYPE}Value wrapper = ({CAPITALIZED_TYPE}Value) o;
        return value == wrapper.value;
    }

    @Override
    public int hashCode() {
        return {PRIMITIVE_TYPE}.hashCode(value);
    }

    @Override
    public String toString() {
        return "{CAPITALIZED_TYPE}Value{" + "value=" + value + '}';
    }
}
```

**Examples of this pattern:**

- `LongValue` for `long`
- `DoubleValue` for `double`
- `BooleanValue` for `boolean`
- `FloatValue` for `float`
- `ByteValue` for `byte`

---

## Best Practices

### 1. **Simple = Better**

Keep wrappers simple. They should only hold the wrapped value(s).

```java
// ✅ Good: Simple wrapper
public class IntValue {
    private int value;
    // No business logic
}

// ❌ Bad: Too much logic in wrapper
public class IntValue {
    private int value;
    private Cache cache;
    private Logger logger;
    public void calculateFactorial() { ... }
}
```

### 2. **Include Both Constructors**

Always provide both no-arg and convenience constructors:

```java
public class IntValue {
    public IntValue() {}                    // For Jackson
    public IntValue(int value) { ... }     // For users
}
```

### 3. **Use Appropriate Getter Names**

For simple wrappers with a single value, use `getValue()` consistently:

```java
IntValue wrapper = new IntValue(42);
int value = wrapper.getValue();  // Clear intent
```

### 4. **Document the Purpose**

Add JavaDoc explaining why the wrapper exists:

```java
/**
 * Wrapper for primitive int values.
 *
 * Quarkus EasyNATS requires Jackson-compatible types, which means
 * primitive types must be wrapped in a POJO. Use this wrapper when
 * publishing/subscribing to individual integer values.
 *
 * @see com.example.LongValue
 * @see com.example.DoubleValue
 */
public class IntValue {
    // ...
}
```

### 5. **Consider Using Records (Java 14+)**

Records eliminate boilerplate and are immutable:

```java
public record IntValue(int value) {}

// That's it! Jackson will generate constructors, getters, equals(), etc.
// Use as: publisher.publish(new IntValue(42));
```

### 6. **Test the Wrapper**

Test serialization/deserialization:

```java
@Test
void testIntValueRoundtrip() {
    IntValue original = new IntValue(42);

    ObjectMapper mapper = new ObjectMapper();
    byte[] json = mapper.writeValueAsBytes(original);
    IntValue restored = mapper.readValue(json, IntValue.class);

    assertEquals(original, restored);
}
```

---

## When NOT to Use Wrapper Pattern

Don't create wrappers for types that are already supported:

```java
// ❌ Don't do this
public class StringWrapper {
    private String value;
}
NatsPublisher<StringWrapper> publisher;

// ✅ Do this instead
NatsPublisher<String> publisher;
```

---

## Complete Example: Wrapping Multiple Primitives

Here's a complete working example with multiple wrapper types:

```java
// File: IntValue.java
public class IntValue {
    private int value;
    public IntValue() {}
    public IntValue(int value) { this.value = value; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
}

// File: LongValue.java
public class LongValue {
    private long value;
    public LongValue() {}
    public LongValue(long value) { this.value = value; }
    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }
}

// File: NumberService.java
@Dependent
public class NumberService {
    private final NatsPublisher<IntValue> intPublisher;
    private final NatsPublisher<LongValue> longPublisher;

    public NumberService(
        @NatsSubject("numbers.int") NatsPublisher<IntValue> intPublisher,
        @NatsSubject("numbers.long") NatsPublisher<LongValue> longPublisher
    ) {
        this.intPublisher = intPublisher;
        this.longPublisher = longPublisher;
    }

    public void publishInt(int value) throws Exception {
        intPublisher.publish(new IntValue(value));
    }

    public void publishLong(long value) throws Exception {
        longPublisher.publish(new LongValue(value));
    }
}

// File: NumberListener.java
@Singleton
public class NumberListener {
    @NatsSubscriber("numbers.int")
    public void handleInt(IntValue container) {
        System.out.println("Int: " + container.getValue());
    }

    @NatsSubscriber("numbers.long")
    public void handleLong(LongValue container) {
        System.out.println("Long: " + container.getValue());
    }
}
```

---

## See Also

- [JACKSON_COMPATIBILITY_GUIDE.md](./JACKSON_COMPATIBILITY_GUIDE.md) - Which types are supported
- [ERROR_TROUBLESHOOTING.md](./ERROR_TROUBLESHOOTING.md) - Error resolution
- [JACKSON_ANNOTATIONS_GUIDE.md](./JACKSON_ANNOTATIONS_GUIDE.md) - Using Jackson annotations
