# Data Model & Contracts: Typed Subscriber

**Phase**: 1 - Design & Contracts
**Date**: 2025-10-27
**Status**: Complete

## Data Entities

### 1. CloudEvent Message (NATS Message Headers + Payload)

**Source**: NATS JetStream message delivered by JNats client (binary-mode CloudEvents only)

**NATS Message Headers** (carrier format per CloudEvents 1.0 binary-mode spec):
```
ce-specversion: 1.0
ce-type: com.example.OrderCreated
ce-source: /ordering/api
ce-id: 123e4567-e89b-12d3-a456-426614174000
ce-time: 2025-10-27T10:30:00Z
ce-datacontenttype: application/json
```

**NATS Message Payload**: Encoded event data (string or binary, never the CloudEvent envelope)

**Processing**:
1. JNats client delivers Message with headers map and payload bytes
2. CloudEventUnwrapper reads headers with `ce-` prefix
3. Validates required binary-mode headers (specversion, type, source, id)
4. Extracts payload bytes from Message.getData() - this is the event data
5. Returns decoded/deserialized object to subscriber method

**Validation Rules**:
- `ce-specversion` MUST be "1.0"
- `ce-type` MUST be present (identifies event type)
- `ce-source` MUST be present (identifies origin)
- `ce-id` MUST be present (unique event identifier)
- `ce-datacontenttype` optional (defaults to "application/json")
- Structured-mode NOT supported (entire CloudEvent must NOT be in payload)
- Unknown headers ignored (forward compatibility)

---

### 2. Typed Message Object

**Source**: Result of CloudEvent data deserialization using strategy matching `TypedPayloadEncoder`

**Attributes** (varies by application):
```java
// Example 1: Complex type (POJO)
class Order {
    String orderId;          // From event data JSON
    String customerId;       // From event data JSON
    List<OrderItem> items;   // From event data JSON
    BigDecimal totalPrice;   // From event data JSON
}

// Example 2: Record
record User(String userId, String name, String email) {}

// Example 3: Generic type
List<Notification>   // Decoded from JSON array

// Example 4: Primitive wrapper
Integer count       // Decoded from UTF-8 string

// Example 5: Byte array
byte[] binaryData   // Base64-decoded

// Example 6: Primitive array
int[] numbers       // Space-separated integers

// Example 7: String array
String[] tags       // Comma-separated strings
```

**Deserialization Process**:
1. CloudEventUnwrapper extracts payload bytes from NATS message
2. Determine appropriate decoder strategy based on parameter type (matches TypedPayloadEncoder)
3. Decode/deserialize payload according to strategy:
   - **Native encoding**: Primitive wrappers, String → Direct UTF-8 decoding
   - **Byte array encoding**: byte[], Byte → Base64 decoding
   - **Array encoding**: int[], long[], etc. → Space-separated parsing; String[] → Comma-separated parsing
   - **Complex encoding**: POJOs, records, generics → Jackson JSON deserialization
4. Return typed object to subscriber method

**Supported Types** (mirrors TypedPayloadEncoder):

✅ **Primitive Wrappers** (encoded as UTF-8 strings):
- Integer, Long, Double, Float, Boolean, Short, Character
- Example: Integer value = 42 → "42" (UTF-8 bytes)

✅ **String** (direct UTF-8):
- Example: "hello" → UTF-8 bytes

✅ **Byte Types** (base64-encoded):
- byte[], Byte
- Example: byte[] {1, 2, 3} → "AQID" (base64)

✅ **Primitive Arrays** (space-separated):
- int[], long[], double[], float[], boolean[], short[], char[]
- Example: int[] {1, 2, 3} → "1 2 3" (UTF-8 bytes)

✅ **String Arrays** (comma-separated):
- String[]
- Example: {"a", "b", "c"} → "a,b,c" (UTF-8 bytes)

✅ **Complex Types** (JSON):
- POJOs with no-arg constructor or @JsonCreator
- Java records (Java 21+)
- Generic types: `List<T>`, `Map<K,V>`, `Set<T>`, etc.

❌ **NOT Supported**:
- Raw primitives: int, long, boolean (use wrapper types instead)
- Interfaces without implementation (Jackson cannot instantiate)
- Abstract classes without @JsonDeserialize configuration

**Symmetry with TypedPayloadEncoder**: The subscriber supports receiving exactly the types that NatsPublisher can send - true bidirectional type support.

---

## Runtime Contracts

### Contract 1: CloudEventUnwrapper

**Input**: NATS Message (CloudEvents binary-mode only)

**Output**: Extracted data (byte[])

**CloudEvents Format**: Binary-mode ONLY
- Attributes (ce-specversion, ce-type, ce-source, ce-id, etc.) stored in NATS message headers with `ce-` prefix
- Event data in NATS message payload (binary or JSON string)
- Structured-mode (entire CloudEvent in payload) is NOT supported
- Reference: https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md

**Interface** (runtime module):
```java
public class CloudEventUnwrapper {
    /**
     * Unwrap NATS message (binary-mode CloudEvents) to extract event data.
     *
     * @param message NATS message with ce-* headers (binary-mode CloudEvents)
     * @return extracted event data bytes from message payload
     * @throws CloudEventException if message is not valid CloudEvents 1.0 binary-mode
     */
    public static byte[] unwrapData(io.nats.client.Message message)
        throws CloudEventException;
}

public class CloudEventException extends RuntimeException {
    // Thrown when:
    // - ce-specversion header missing or not "1.0"
    // - ce-type, ce-source, ce-id headers missing
    // - message does not follow CloudEvents 1.0 binary-mode protocol
    // - structured-mode detected (CloudEvent envelope in payload instead of headers)
}
```

**Processing Steps** (binary-mode):
1. Read headers map from NATS message
2. Validate required `ce-` headers present (specversion, type, source, id)
3. Validate `ce-specversion == "1.0"`
4. Verify binary-mode (attributes NOT in payload JSON)
5. Extract message data payload (this is the event data, not the CloudEvent envelope)
6. Return data bytes for further deserialization

---

### Contract 2: MessageDeserializer

**Input**: Extracted CloudEvent data (byte[]), target type, ObjectMapper

**Output**: Deserialized typed object (or thrown exception)

**Decoding Strategy** (mirrors TypedPayloadEncoder):
1. Check if type can be decoded natively (primitive wrappers, String, byte types, arrays)
2. If native: use native decoder (UTF-8, base64, space-separated, comma-separated parsing)
3. If complex: use Jackson ObjectMapper for JSON deserialization

**Interface** (runtime module):
```java
public class MessageDeserializer {
    /**
     * Deserialize CloudEvent data into typed object using appropriate decoder.
     * Automatically chooses decoder strategy based on target type:
     * - Native types: Direct UTF-8, base64, space-separated, or comma-separated parsing
     * - Complex types: Jackson JSON deserialization
     *
     * @param data extracted event data bytes
     * @param targetType the Java type to deserialize into
     * @param objectMapper Jackson ObjectMapper to use (for complex types only)
     * @return deserialized object of type T
     * @throws DeserializationException if data cannot be deserialized
     */
    public static <T> T deserialize(
        byte[] data,
        Class<T> targetType,
        ObjectMapper objectMapper
    ) throws DeserializationException;

    /**
     * Variant supporting generic types (List<User>, etc.)
     */
    public static <T> T deserialize(
        byte[] data,
        com.fasterxml.jackson.core.type.TypeReference<T> typeRef,
        ObjectMapper objectMapper
    ) throws DeserializationException;
}

public class DeserializationException extends RuntimeException {
    // Thrown when:
    // - Native decoding fails (e.g., "abc" → Integer)
    // - Base64 decoding fails
    // - Space-separated or comma-separated parsing fails
    // - JSON parsing fails
    // - Type conversion fails
    // - Required field missing
    // - Setter/constructor not found
}
```

**Processing Steps** (native types):
1. Determine if targetType is natively encodable (check TypedPayloadEncoder.canEncodeNatively())
2. **If native type**:
   - UTF-8 string types (String, primitive wrappers): Decode UTF-8 string, parse/convert to target type
   - Byte arrays: Base64-decode the string
   - Primitive arrays: Split space-separated string, parse each element
   - String arrays: Split comma-separated string
3. **If complex type**:
   - Decode byte[] to UTF-8 string
   - Invoke ObjectMapper.readValue(json, targetType)
4. Catch exceptions → wrap as DeserializationException
5. Return deserialized object or throw

**Example Decodings**:
```
Input: Integer type, byte[] [49, 50, 51] ("123")
→ UTF-8 decode: "123"
→ Integer.parseInt("123")
→ Result: Integer(123)

Input: byte[] type, byte[] [65, 81, 73, 68] ("AQID" base64)
→ Base64 decode
→ Result: byte[] {1, 2, 3}

Input: int[] type, byte[] [49, 32, 50, 32, 51] ("1 2 3")
→ UTF-8 decode: "1 2 3"
→ Split on space: ["1", "2", "3"]
→ Parse each: [1, 2, 3]
→ Result: int[] {1, 2, 3}

Input: String[] type, byte[] [97, 44, 98, 44, 99] ("a,b,c")
→ UTF-8 decode: "a,b,c"
→ Split on comma: ["a", "b", "c"]
→ Result: String[] {"a", "b", "c"}

Input: Order type, byte[] (JSON bytes)
→ UTF-8 decode: "{"orderId":"ORD-123", ...}"
→ Jackson deserialize
→ Result: Order instance
```

---

### Contract 3: SubscriberMessageHandler (Enhanced from 004)

**Changes to 004 handler**:

**Before** (004-nats-subscriber-mvp):
```java
// 004: Handles String-only messages
Message msg = nextMessage();
String payload = new String(msg.getData(), UTF_8);
method.invoke(instance, payload);  // assume method(String)
```

**After** (006-typed-subscriber):
```java
// 006: Handles CloudEvents with typed deserialization
Message msg = nextMessage();

// Step 1: Unwrap CloudEvent
byte[] eventData = CloudEventUnwrapper.unwrapData(msg);

// Step 2: Deserialize to typed object
Object typedPayload = MessageDeserializer.deserialize(
    eventData,
    parameterType,  // resolved at build time
    objectMapper
);

// Step 3: Invoke method with typed object
method.invoke(instance, typedPayload);

// Step 4: Ack on success, nak on exception (inherited from 004)
```

---

### Contract 4: SubscriberProcessor (Enhanced from 004)

**Build-time processor enhancements**:

```java
public class SubscriberProcessor {
    // 004: Validates method has exactly 1 String parameter
    // 006: Validates parameter is Jackson-deserializable type

    private void validateSubscriberMethod(MethodInfo method) {
        // 1. Check method has exactly 1 parameter (inherited from 004)
        if (method.parameters().size() != 1) {
            throw error(...);
        }

        // 2. NEW: Check parameter is not String (String = 004 scope)
        Type paramType = method.parameterType(0);
        if (paramType.name().toString().equals("java.lang.String")) {
            throw error("006 requires typed objects, not String. Use 004 for String-only.");
        }

        // 3. NEW: Check parameter is Jackson-deserializable
        try {
            TypeFactory typeFactory = ObjectMapper.getTypeFactory();
            JavaType javaType = typeFactory.constructType(paramType.asClassType());
            // If we reach here, type is valid for Jackson
        } catch (Exception e) {
            throw error("Parameter type not Jackson-deserializable: " + paramType);
        }
    }
}
```

**Build-time Contract**:
- Input: @NatsSubscriber-annotated method in Java source
- Output: Bytecode record (internal artifact) including resolved parameter type
- Validation: Fail build if type not deserializable
- Error message example: `"Method processOrder(Order) parameter Order is not Jackson-deserializable. Ensure it has a no-arg constructor or @JsonCreator annotation."`

---

## State Transitions

### Message Processing Lifecycle

```
[NATS Message Arrives]
        ↓
[CloudEventUnwrapper.unwrapData()]
        ├─→ SUCCESS: Return event data bytes
        └─→ FAIL: CloudEventException (nack, log, continue)
        ↓
[MessageDeserializer.deserialize()]
        ├─→ SUCCESS: Return typed object
        └─→ FAIL: DeserializationException (nack, log, continue)
        ↓
[Subscriber Method Invocation]
        ├─→ SUCCESS: Implicit ack
        └─→ FAIL: Exception (implicit nack, log, continue)
```

**Nack Behavior** (inherited from 004):
- Message becomes eligible for redelivery
- Subject to consumer retry policy (configured in NATS)
- Default: JNats uses exponential backoff

---

## Validation Rules Summary

| Rule | Check | Impact |
|------|-------|--------|
| CloudEvent `ce-specversion` = "1.0" | Runtime | Nack + log if missing |
| CloudEvent `ce-type` present | Runtime | Nack + log if missing |
| CloudEvent `ce-source` present | Runtime | Nack + log if missing |
| CloudEvent `ce-id` present | Runtime | Nack + log if missing |
| Parameter type is Jackson-deserializable | Build-time | Fail build if not |
| Parameter type ≠ String | Build-time | Fail build if String (use 004 instead) |
| Method has exactly 1 parameter | Build-time | Fail build if 0 or 2+ (inherited from 004) |
| Deserialized object ≠ null | Runtime | Nack + log if null |

---

## Example Data Flow

### Scenario: Order Processing Application

**CloudEvent in NATS** (headers + payload):
```
Headers:
  ce-specversion: 1.0
  ce-type: com.example.OrderCreated
  ce-source: /ordering/api
  ce-id: order-789
  ce-datacontenttype: application/json

Payload (JSON):
{
  "orderId": "ORD-123",
  "customerId": "CUST-456",
  "items": [
    {"sku": "ITEM-001", "qty": 2},
    {"sku": "ITEM-002", "qty": 1}
  ],
  "totalPrice": 99.99
}
```

**Subscriber Method** (source code):
```java
@NatsSubscriber(subject = "orders.created")
public void handleOrderCreated(Order order) {
    System.out.println("Processing order: " + order.orderId);
    // Process typed Order object
}

class Order {
    String orderId;
    String customerId;
    List<OrderItem> items;
    BigDecimal totalPrice;
}
```

**Runtime Processing**:
1. JNats delivers Message with headers and payload
2. CloudEventUnwrapper validates headers, extracts JSON payload
3. MessageDeserializer deserializes JSON → Order instance
4. Subscriber method invoked with Order(orderId="ORD-123", ...)
5. Method completes successfully → implicit ack
6. Message removed from queue

---

## Inheritance from 004-nats-subscriber-mvp

The following contracts are inherited and unchanged:

- Ephemeral consumer creation (no durable consumer support)
- Implicit ack/nak mechanism
- Error logging at ERROR level
- Multiple @NatsSubscriber methods per class (each creates own consumer)
- NATS connection lifecycle management
- Method invocation mechanism (reflection-based)

This feature extends 004 purely at the deserialization layer.
