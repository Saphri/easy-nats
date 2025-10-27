# Contract: Error Handling

**Feature**: 007-typed-serialization
**Date**: 2025-10-27

## Overview

This document defines the error types and handling semantics for typed message serialization/deserialization.

---

## Exception Hierarchy

```
Exception
  ├─ SerializationException (checked)
  │   └── cause: JsonProcessingException (from Jackson)
  │
  └─ DeserializationException (checked)
      └── cause: IOException or JsonMappingException (from Jackson)

Extensions inherit from EasyNatsException (checked):
  ├─ SerializationException
  ├─ DeserializationException
  └─ NatsPublishException (existing)
```

---

## SerializationException

**Purpose**: Indicates that a typed message could not be serialized to JSON.

```java
public class SerializationException extends Exception {
    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### When Thrown

Thrown when `TypedPayloadEncoder.encodeWithJackson()` fails:
- Circular object reference (not possible to serialize)
- Unknown serialization error from Jackson
- ObjectMapper configuration issues

### Error Message Format

```
"Failed to serialize [TypeName]: [Jackson root cause message]"

Examples:
- "Failed to serialize OrderData: Infinite recursion with class Customer"
- "Failed to serialize OrderData: No serializer found for type"
- "Failed to serialize OrderData: Cannot serialize BigDecimal value due to per-value override"
```

### Cause Chain

```
SerializationException
  message: "Failed to serialize OrderData: ..."
  cause: JsonProcessingException (from Jackson)
    message: "Infinite recursion with class Customer"
    cause: (Jackson internal exception)
```

### Handling

**In Publisher Code** (user application):

```java
try {
    orderPublisher.publish(order);
} catch (SerializationException e) {
    logger.error("Failed to publish order: {}", order, e);
    // Handle error: retry, dead-letter queue, etc.
}
```

**In Extension Code** (DefaultMessageHandler, TypedPayloadEncoder):

```java
try {
    return objectMapper.writeValueAsBytes(value);
} catch (JsonProcessingException e) {
    String className = value.getClass().getSimpleName();
    throw new SerializationException(
        "Failed to serialize " + className + ": " + e.getMessage(), e
    );
}
```

### Logging

When caught in extension:
```
ERROR: "Failed to serialize OrderData: Infinite recursion with class Customer"
  Cause: com.fasterxml.jackson.databind.JsonMappingException: Infinite recursion...
  Payload: OrderData(id=ORD-001, amount=150.00, customer=...)
```

---

## DeserializationException

**Purpose**: Indicates that a received message could not be deserialized to the target type.

```java
public class DeserializationException extends Exception {
    public DeserializationException(String message) {
        super(message);
    }

    public DeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### When Thrown

Thrown when `MessageDeserializer.deserialize()` fails:
- Invalid JSON format
- JSON doesn't match target type structure
- Missing required fields
- Type mismatch (e.g., number in string field)
- Type validation failures (at registration or runtime)

### Error Message Format

```
"Failed to deserialize to type [TypeName]: [Jackson root cause message]"

Examples:
- "Failed to deserialize to type OrderData: Unexpected character ('n' (code 110))"
- "Failed to deserialize to type OrderData: Missing required creator property 'id'"
- "Failed to deserialize to type OrderData: Cannot construct instance of ..., problem: ..."
- "Type OrderData is not supported. Wrap it in a POJO"
```

### Cause Chain

```
DeserializationException
  message: "Failed to deserialize to type OrderData: ..."
  cause: JsonMappingException (from Jackson)
    message: "Missing required creator property 'id'"
    cause: (Jackson internal exception)
```

### Context Information Logged (not in exception message)

When deserialization fails, context logged separately includes:
- **Target type**: `OrderData`
- **Raw payload** (first 1000 chars): `{"amount": 150.00}`
- **Root cause**: "Missing required field: id"
- **Actionable suggestion**: "Ensure JSON includes all required fields"

**Log Format**:
```
ERROR DefaultMessageHandler: "Message deserialization failed for subject=orders, method=handleOrder, type=OrderData, cause=Missing required creator property 'id'"
DEBUG: Raw payload: {"amount": 150.00}
```

### Handling

**In Subscriber** (user application):

Subscribers don't catch this exception. The framework handles it:
1. Logs the error
2. Automatically NAKs the message (redelivery by NATS)
3. Continues processing next message

**If user wants custom error handling**: Create a validation wrapper or custom deserializer

```java
// Option 1: Wrap in a POJO with validation
public class OrderDataWrapper {
    public OrderData order;

    @JsonDeserialize(using = SafeOrderDeserializer.class)
    public String deserializationError;

    public OrderDataWrapper() {}
}

// Option 2: Custom deserializer with fallback
public class SafeOrderDeserializer extends StdDeserializer<OrderData> {
    public SafeOrderDeserializer() { super(OrderData.class); }

    @Override
    public OrderData deserialize(JsonParser p, DeserializationContext ctxt) {
        try {
            // Normal deserialization
        } catch (IOException e) {
            // Fallback or default
            logger.error("Deserialization failed", e);
            return new OrderData("UNKNOWN", BigDecimal.ZERO);
        }
    }
}
```

**In Extension Code** (DefaultMessageHandler, MessageDeserializer):

```java
try {
    return objectMapper.readValue(eventData, parameterType);
} catch (Exception e) {
    throw new DeserializationException(
        "Failed to deserialize to type " + parameterType.getTypeName(), e
    );
}
```

### Logging

When caught in extension:
```
ERROR DefaultMessageHandler: "Message deserialization failed for subject=orders, method=handleOrder, type=OrderData"
DEBUG: "Root cause: Missing required creator property 'id'"
DEBUG: "Raw payload: {\"amount\": 150.00}"
DEBUG: "Suggestion: Ensure JSON includes required field 'id'"
```

---

## Type Validation Errors

**Purpose**: Report unsupported types at registration (publisher/subscriber creation or app startup).

### Error Messages

#### Primitive Type
```
"Type 'int' is not supported. Wrap it in a POJO:
public class IntValue {
    public int value;
    public IntValue() {}
}"
```

#### Array Type
```
"Type 'String[]' is not supported. Wrap it in a POJO:
public class StringList {
    public String[] items;
    public StringList() {}
}"
```

#### Missing No-Arg Constructor
```
"Type 'OrderData' requires a no-arg constructor for Jackson deserialization.
Add a no-arg constructor or use @JsonDeserialize with a custom deserializer.
Example:
public class OrderData {
    public OrderData() {}  // Add this
}"
```

#### Unresolvable Generic
```
"Type 'OrderData<T>' has unresolvable generic parameter 'T'.
Provide concrete type or use wrapper:
@NatsSubscriber(subject = \"orders\", consumer = \"processor\")
public void handle(OrderData<ConcreteType> order) {}  // ✓ OK
"
```

### Handling

**At Publisher/Subscriber Registration**:
```java
try {
    // Register publisher or subscriber
} catch (IllegalArgumentException e) {
    // Type validation failed
    logger.error("Cannot register: {}", e.getMessage());
    System.exit(1);  // Fail fast
}
```

Error logged at startup or injection time (fail-fast behavior).

---

## NATS Publishing Errors

**Type**: `NatsPublishException` (existing, not new)

**When Thrown**: Publication to NATS JetStream fails (network error, broker down, etc.)

**Handling in TypedPublisher**:
```java
try {
    jetStream.publish(subject, headers, payload);
} catch (IOException | JetStreamApiException e) {
    throw new NatsPublishException("Failed to publish message", e);
}
```

---

## Message Acknowledgment on Error

### Scenario 1: Deserialization Failure

```
NATS Message Arrives
  ↓
Deserialize to OrderData
  ↓
DeserializationException thrown
  ↓
Exception caught by DefaultMessageHandler
  ↓
Error logged with target type and raw payload
  ↓
Message NAK'd (redelivery by NATS)
  ↓
Subscriber method NOT invoked
```

### Scenario 2: Subscriber Method Exception

```
NATS Message Arrives
  ↓
Deserialize to OrderData ✓
  ↓
Invoke subscriber method: handleOrder(order)
  ↓
Exception thrown in method
  ↓
Exception caught by DefaultMessageHandler
  ↓
Exception logged with subscriber details
  ↓
Message NAK'd (redelivery by NATS)
  ↓
Continue to next message
```

### Scenario 3: Success

```
NATS Message Arrives
  ↓
Deserialize to OrderData ✓
  ↓
Invoke subscriber method: handleOrder(order)
  ↓
Method returns normally ✓
  ↓
Message ACK'd
  ↓
Continue to next message
```

---

## Error Logging Standards

All errors logged with:
1. **Log Level**:
   - ERROR for all serialization/deserialization failures
   - ERROR for configuration/validation errors
   - ERROR for unexpected exceptions

2. **Log Format**:
   ```
   ERROR [Class]: "[Human-readable error message]"
   [Additional context: type, payload, suggestion]
   [Stack trace if applicable]
   ```

3. **Sensitive Information**:
   - Do NOT log full message payload if size > 1000 chars
   - Log first 1000 chars for debugging
   - Do NOT log authentication credentials
   - Do NOT log internal stack traces to user (only to logs)

---

## Testing Error Cases

Implement tests for all error scenarios:

1. **SerializationException**:
   - Circular reference
   - Unknown Jackson error

2. **DeserializationException**:
   - Malformed JSON
   - Missing required fields
   - Type mismatch
   - Extra unexpected fields

3. **Type Validation Errors**:
   - Primitive types rejected
   - Array types rejected
   - Types without no-arg constructor rejected
   - Unresolvable generics rejected

4. **Subscriber Error Handling**:
   - Deserialization error NAKs message
   - Subscriber method exception NAKs message
   - Success case ACKs message

5. **Error Logging**:
   - Errors logged with target type
   - Raw payload included (first 1000 chars)
   - Root cause included
   - Actionable suggestions included

