# Data Model: Typed Serialization

**Feature**: 007-typed-serialization
**Date**: 2025-10-27

## Overview

This document defines the key data types and validation rules for typed message serialization/deserialization in the EasyNATS Quarkus extension.

---

## Core Types

### 1. MessageType<T>

**Purpose**: Encapsulates a user-provided type with validation metadata for serialization/deserialization.

```java
public class MessageType<T> {
    private final Class<T> rawClass;
    private final JavaType jacksonType;
    private final TypeValidationResult validationResult;
    private final ObjectMapper objectMapper;
}
```

**Fields**:
- `rawClass: Class<T>` - The Java class (POJO, record, etc.)
- `jacksonType: JavaType` - Jackson's internal type representation (from `ObjectMapper.getTypeFactory().constructType()`)
- `validationResult: TypeValidationResult` - Result of type introspection validation
- `objectMapper: ObjectMapper` - The CDI-injected ObjectMapper for serialization/deserialization

**Invariants**:
- `validationResult.isValid()` must be true before the type is used for publishing/subscribing
- `jacksonType` must not be null (Jackson construction succeeded)
- `objectMapper` must be the default CDI-provided instance (no custom mappers)

**Usage**:
- Publisher: Validate type when publisher is created
- Subscriber: Validate type when subscriber method is registered
- Serialization: Use `objectMapper.writeValueAsBytes(payload, jacksonType)`
- Deserialization: Use `objectMapper.readValue(data, jacksonType)`

---

### 2. TypeValidationResult

**Purpose**: Encapsulates the outcome of type introspection validation.

```java
public class TypeValidationResult {
    private final boolean valid;
    private final String typeName;
    private final String errorMessage; // null if valid
    private final ValidationErrorType errorType; // null if valid
}

public enum ValidationErrorType {
    PRIMITIVE_TYPE,           // int, long, double, etc.
    ARRAY_TYPE,              // int[], String[], etc.
    MISSING_NO_ARG_CTOR,     // No no-arg constructor and no @JsonDeserialize
    UNRESOLVABLE_GENERIC,    // Generic parameter cannot be resolved
    JACKSON_ERROR,           // Jackson type construction failed
    CUSTOM_ERROR             // Other validation error
}
```

**Fields**:
- `valid: boolean` - true if type is Jackson-compatible and supported
- `typeName: String` - Fully qualified class name (e.g., "com.example.OrderData")
- `errorMessage: String` - Human-readable error (null if valid)
- `errorType: ValidationErrorType` - Categorizes the error type (null if valid)

**Invariants**:
- If `valid == true`, `errorMessage` and `errorType` must be null
- If `valid == false`, both `errorMessage` and `errorType` must be non-null
- Error messages must include actionable guidance (e.g., "Wrap this type in a POJO")

**Example Valid Result**:
```
valid: true
typeName: com.example.OrderData
errorMessage: null
errorType: null
```

**Example Invalid Result** (primitive):
```
valid: false
typeName: int
errorMessage: "Primitive type 'int' is not supported. Wrap it in a POJO: class IntValue { int value; }"
errorType: PRIMITIVE_TYPE
```

**Example Invalid Result** (missing no-arg constructor):
```
valid: false
typeName: com.example.OrderData
errorMessage: "Type 'OrderData' requires a no-arg constructor for Jackson deserialization. Add a no-arg constructor or use @JsonDeserialize with a custom deserializer."
errorType: MISSING_NO_ARG_CTOR
```

---

### 3. SerializationContext

**Purpose**: Metadata and state for serializing a typed message.

```java
public class SerializationContext<T> {
    private final MessageType<T> messageType;
    private final T payload;
    private final long serializationStartTime;
}
```

**Fields**:
- `messageType: MessageType<T>` - The validated type definition
- `payload: T` - The object to serialize
- `serializationStartTime: long` - System time when serialization began (for error logging)

**Invariants**:
- `messageType.validationResult.isValid()` must be true
- `payload` must be an instance of `messageType.rawClass`
- `payload` must be serializable by Jackson (no cycles, all fields Jackson-compatible)

**Usage**:
- Created when `TypedPublisher.publish(T payload)` is called
- Passed to `TypedPayloadEncoder.encode(SerializationContext)`
- If serialization fails, context used to construct error message with payload type

---

### 4. DeserializationContext

**Purpose**: Metadata and state for deserializing a message to a typed object.

```java
public class DeserializationContext<T> {
    private final MessageType<T> messageType;
    private final byte[] rawPayload;      // Original JSON bytes from NATS message
    private final long deserializationStartTime;
}
```

**Fields**:
- `messageType: MessageType<T>` - The validated type definition
- `rawPayload: byte[]` - The JSON bytes received from NATS
- `deserializationStartTime: long` - System time when deserialization began (for error logging)

**Invariants**:
- `messageType.validationResult.isValid()` must be true
- `rawPayload` must be valid UTF-8 encoded JSON (or deserialization will fail with clear error)

**Usage**:
- Created when a message arrives for a typed subscriber
- Passed to `MessageDeserializer.deserialize(DeserializationContext)`
- If deserialization fails, context used to construct error message with type and raw payload

**Error Logging**:
When deserialization fails, log must include:
- Target type: `messageType.typeName`
- Root cause: Exception message from Jackson
- Raw payload (for debugging): First 1000 characters of `rawPayload` as UTF-8 string
- Suggestion: "If the JSON structure is correct but the type is missing a no-arg constructor, use @JsonDeserialize with a custom deserializer"

---

### 5. Jackson-Compatible Type Requirements

**Supported Types**:
- POJOs with public no-arg constructor (default constructor)
- Java records (Java 14+)
- Types with Jackson annotations (`@JsonDeserialize`, `@JsonProperty`, `@JsonIgnore`, etc.)
- Generic types (List<OrderData>, Map<String, OrderData>, etc.)

**Example Supported Types**:

```java
// POJO with no-arg constructor
public class OrderData {
    public String id;
    public BigDecimal amount;

    public OrderData() {}  // Required
    public OrderData(String id, BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }
}

// Java record
public record Product(String name, double price) {}

// Generic type
public class Container<T> {
    public T value;
    public Container() {}
    public Container(T value) { this.value = value; }
}

// With Jackson annotations
public class SpecialData {
    @JsonProperty("custom_name")
    public String name;

    @JsonIgnore
    public transient long timestamp;

    public SpecialData() {}
    public SpecialData(String name) { this.name = name; }
}

// With custom deserializer
public class CustomType {
    public String data;

    @JsonDeserialize(using = CustomDeserializer.class)
    public CustomType(String data) {
        this.data = deserialize(data);
    }
}
```

**Unsupported Types** (must be wrapped):
- Primitives: `int`, `long`, `double`, `boolean`, etc.
- Primitive wrappers (when used alone): `Integer`, `Long`, `Double`
- Arrays: `int[]`, `String[]`, `byte[]`, etc.
- Types without no-arg constructor (unless custom deserializer via `@JsonDeserialize`)
- Abstract classes
- Interfaces

**Wrapper Pattern** (for unsupported types):

```java
// Problem: User has type that's not Jackson-compatible
public class LegacyData {
    private int value;  // No no-arg constructor

    public LegacyData(int value) {
        this.value = value;
    }
}

// Solution: Wrap in a Jackson-compatible POJO
public class LegacyDataWrapper {
    public int value;  // Public field or getter

    public LegacyDataWrapper() {}  // No-arg constructor required

    public LegacyDataWrapper(int value) {
        this.value = value;
    }

    // Convert to/from legacy type
    public LegacyData toLegacy() {
        return new LegacyData(value);
    }

    public static LegacyDataWrapper fromLegacy(LegacyData data) {
        return new LegacyDataWrapper(data.getValue());
    }
}
```

---

## Validation Rules

### Type Introspection Validation (at registration)

Performed via `ObjectMapper.getTypeFactory().constructType(Class<?> type)`:

1. **Primitive type check**: Reject `int`, `long`, `double`, `boolean`, `char`, `byte`, `short`, `float`
   - Error: `PRIMITIVE_TYPE`

2. **Array type check**: Reject `int[]`, `String[]`, `byte[]`, etc.
   - Error: `ARRAY_TYPE`

3. **Generic parameter resolution**: Attempt to construct Jackson `JavaType` for generic parameters
   - If fails: `UNRESOLVABLE_GENERIC`

4. **No-arg constructor check**: Attempt to determine if Jackson can construct the type
   - Use `@JsonDeserialize` if custom construction needed
   - Error: `MISSING_NO_ARG_CTOR`

5. **Jackson introspection**: Run `ObjectMapper.getTypeFactory().constructType()` and catch exceptions
   - Any exception: `JACKSON_ERROR`

### Runtime Validation (at serialization/deserialization)

1. **Serialization**: Call `ObjectMapper.writeValueAsBytes(payload, jacksonType)`
   - Catch: `com.fasterxml.jackson.core.JsonProcessingException`
   - Error message includes target type

2. **Deserialization**: Call `ObjectMapper.readValue(rawPayload, jacksonType)`
   - Catch: `java.io.IOException` and `com.fasterxml.jackson.databind.JsonMappingException`
   - Error message includes target type AND raw payload (first 1000 chars)

---

## Error Handling

### Serialization Errors

**Exception Type**: `SerializationException extends Exception`

**Cause Hierarchy**:
```
SerializationException
  cause: JsonProcessingException (from Jackson)
    cause: Original Jackson error (e.g., JsonGenerationException)
```

**Error Message Pattern**:
```
"Failed to serialize [TypeName]: [Jackson error message]"
Example: "Failed to serialize OrderData: No serializer found for type"
```

### Deserialization Errors

**Exception Type**: `DeserializationException extends Exception`

**Cause Hierarchy**:
```
DeserializationException
  cause: IOException or JsonMappingException (from Jackson)
    cause: Original Jackson error
```

**Error Message Pattern**:
```
"Failed to deserialize to type [TypeName]: [Jackson error message]"
Example: "Failed to deserialize to type OrderData: Unresolved type in generic type"

Plus logged context:
"Target type: OrderData"
"Raw payload: {\"id\":\"ORD-001\",\"amount\":150.00}"
"Root cause: Missing no-arg constructor"
```

---

## State Transitions

### Type Lifecycle

```
TypeValidationResult.validate(class)
  ↓
  ├─ INVALID → Error logged, publisher/subscriber creation fails
  │
  └─ VALID → MessageType created
      ↓
      ├─ Publisher.publish(payload)
      │   ↓
      │   SerializationContext created
      │   ↓
      │   Serialization success → Send to NATS
      │   Serialization failure → SerializationException logged
      │
      └─ Subscriber message arrives
          ↓
          DeserializationContext created
          ↓
          Deserialization success → Invoke subscriber method
          Deserialization failure → DeserializationException logged, message NAK'd
```

---

## Storage

**Persistence**: No persistence required
- Types are validated at registration (compile-time or startup)
- MessageType instances are cached in publisher/subscriber beans
- No database or file storage needed

---

## Constraints

| Constraint | Rationale |
|-----------|-----------|
| Single ObjectMapper per app | Quarkus convention, simplifies API |
| No custom type mappings | Users express customization via Jackson annotations |
| No cyclic type references | Jackson doesn't support; users must restructure data |
| No dynamic types | Types determined at compile-time; generics resolved at registration |
| Error messages ≤ 1000 chars | Balance debuggability with log file size |
| Raw payload limited to 1000 chars | Prevent excessive logging for large messages |

