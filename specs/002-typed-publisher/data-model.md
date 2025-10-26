# Data Model: Typed NatsPublisher with CloudEvents Support

**Feature**: MVP 002 Typed NatsPublisher | **Date**: 2025-10-26 | **Phase**: Design Artifacts (Phase 1)

## Entity Definitions

### 1. NatsPublisher<T> (Extended)

**Responsibility**: Type-safe publisher for NATS JetStream messages with optional CloudEvents support.

**Location**: `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisher.java`

**Type Parameters**:
- `<T>`: Generic type parameter representing the payload type (can be any Java class)

**Fields**:
| Field | Type | Visibility | Mutable | Description |
|-------|------|------------|---------|-------------|
| `connectionManager` | `NatsConnectionManager` | private | no | Shared NATS connection manager; injected via constructor |

**Methods** (MVP 002 additions):

#### `publish(T payload): void`
- **Description**: Publish a typed object as JSON to the hardcoded subject "test"
- **Parameters**: `payload: T` - The object to publish (cannot be null)
- **Returns**: void
- **Throws**:
  - `IllegalArgumentException` - If payload is null (message: "Cannot publish null object")
  - `SerializationException` - If object cannot be serialized to JSON (wrapped JsonProcessingException)
- **Type Safety**: Compile-time generic type checking; IDE autocomplete for payload type
- **Encoding Strategy**:
  1. Check if T is primitive (int, long, byte, short, double, float, boolean, char) or String → encode natively
  2. Check if T is array of primitives or String → encode natively
  3. Otherwise → use Jackson ObjectMapper for JSON serialization
- **Error Handling**: User-friendly SerializationException messages (e.g., "Failed to serialize MyClass: missing zero-arg constructor")
- **Example**:
  ```java
  @Singleton
  public class OrderPublisher {
      private final NatsPublisher<Order> orderPublisher;

      OrderPublisher(NatsConnectionManager connectionManager) {
          this.orderPublisher = new NatsPublisher<>(connectionManager);
      }

      public void publishOrder(Order order) {
          orderPublisher.publish(order);  // Type-safe, JSON serialized
      }
  }
  ```

#### `publishCloudEvent(T payload, String ceType, String ceSource): void`
- **Description**: Publish a typed object with CloudEvents metadata headers
- **Parameters**:
  - `payload: T` - The object to publish (cannot be null)
  - `ceType: String` - CloudEvents type (e.g., "com.example.UserCreatedEvent"); auto-generated from class name if null
  - `ceSource: String` - CloudEvents source (e.g., "/myapp"); auto-generated from hostname if null
- **Returns**: void
- **Throws**:
  - `IllegalArgumentException` - If payload is null (message: "Cannot publish null object")
  - `SerializationException` - If object cannot be serialized to JSON
- **CloudEvents Headers Generated**:
  - `ce-type` - Event type (provided or auto-generated from fully-qualified class name)
  - `ce-source` - Event source (provided or auto-generated from hostname/application identifier)
  - `ce-specversion` - Always "1.0" (CloudEvents spec version)
  - `ce-id` - UUID v4 (always auto-generated)
  - `ce-time` - ISO 8601 UTC timestamp (always auto-generated)
  - `ce-datacontenttype` - Always "application/json"
- **Header Format**: Binary content mode - payload in message body, attributes in headers
- **Example**:
  ```java
  Order order = new Order("ORD-123", "alice@example.com", 99.99);
  orderPublisher.publishCloudEvent(order, "com.example.OrderPlaced", "/order-service");

  // Results in NATS message with:
  // Headers: ce-type=com.example.OrderPlaced, ce-source=/order-service, ce-id=<uuid>, ce-time=<iso-8601>
  // Body: {"orderId":"ORD-123","customerEmail":"alice@example.com","amount":99.99}
  ```

**Constructor**:
```java
NatsPublisher(NatsConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
}
```
- **Visibility**: package-private (instantiation via dependency injection in Arc container)
- **Injection**: Constructor-injected dependency for testability and immutability

---

### 2. TypedPayloadEncoder (NEW)

**Responsibility**: Handle encoder/decoder resolution with priority-based selection (primitives → arrays → Jackson).

**Location**: `runtime/src/main/java/org/mjelle/quarkus/easynats/TypedPayloadEncoder.java`

**Type Parameters**: None (utility class with static methods)

**Methods**:

#### `canEncodeNatively(Class<?> type): boolean` (static)
- **Description**: Check if a type can be encoded without Jackson
- **Logic**: Returns true if type is:
  - Primitive wrapper (Integer, Long, Byte, Short, Double, Float, Boolean, Character)
  - `java.lang.String`
  - Byte types (`byte`, `Byte`, `byte[]`) - encoded as base64
  - Array of primitives (int[], long[], etc.) - space-separated
  - Array of String (String[]) - comma-separated
- **Returns**: true if type can be encoded natively; false if Jackson is required
- **Example**:
  ```java
  TypedPayloadEncoder.canEncodeNatively(String.class)      // → true
  TypedPayloadEncoder.canEncodeNatively(int[].class)       // → true
  TypedPayloadEncoder.canEncodeNatively(byte[].class)      // → true (base64 encoded)
  TypedPayloadEncoder.canEncodeNatively(MyEvent.class)     // → false (needs Jackson)
  ```

#### `encodeNatively(Object value): byte[]` (static)
- **Description**: Encode a primitive/String/array to bytes without Jackson
- **Parameters**: `value` - Object to encode (non-null, already verified as native-encodable)
- **Returns**: byte array representation (UTF-8 encoded string; never raw binary)
- **Encoding Rules**:
  - Primitives & String: `toString().getBytes(StandardCharsets.UTF_8)`
  - Primitive arrays: Space-separated string of values, then UTF-8 encoded (e.g., "1 2 3")
  - String arrays: Comma-separated, then UTF-8 encoded (e.g., "a,b,c")
  - **Byte types**: ALWAYS base64-encoded, never raw binary
    - `byte` (single): Base64-encoded byte value
    - `byte[]`: Base64-encoded byte array (using `java.util.Base64.getEncoder().encodeToString(bytes)`)
    - Rationale: Message payloads must be text-based for NATS header compatibility; binary data must be encoded
- **Throws**: None (failures handled by caller)
- **Example**:
  ```java
  TypedPayloadEncoder.encodeNatively(42)                    // → "42".getBytes(UTF_8)
  TypedPayloadEncoder.encodeNatively("hello")               // → "hello".getBytes(UTF_8)
  TypedPayloadEncoder.encodeNatively(new int[]{1,2,3})      // → "1 2 3".getBytes(UTF_8)
  TypedPayloadEncoder.encodeNatively(new byte[]{1,2,3})     // → Base64.getEncoder().encodeToString(new byte[]{1,2,3}).getBytes(UTF_8)
  ```

#### `encodeWithJackson(Object value, ObjectMapper mapper): byte[]` (static)
- **Description**: Encode a complex type to JSON using Jackson
- **Parameters**:
  - `value` - Object to serialize (non-null)
  - `mapper` - Jackson ObjectMapper instance (from Quarkus context)
- **Returns**: JSON-encoded byte array
- **Throws**: `SerializationException` - Wraps `JsonProcessingException` with user-friendly message
- **Error Messages**:
  - "Failed to serialize {ClassName}: {exception message}"
  - Example: "Failed to serialize UserCreatedEvent: missing zero-arg constructor"
- **Example**:
  ```java
  ObjectMapper mapper = quarkusContext.getObjectMapper();
  var event = new UserCreatedEvent("alice", "alice@example.com");
  byte[] json = TypedPayloadEncoder.encodeWithJackson(event, mapper);
  ```

#### `resolveEncoder(Class<?> type): PayloadEncoderStrategy` (static)
- **Description**: Return the appropriate encoder strategy based on type
- **Logic**:
  1. If `canEncodeNatively(type)` → return `NATIVE_ENCODER`
  2. Otherwise → return `JACKSON_ENCODER`
- **Returns**: PayloadEncoderStrategy enum (NATIVE_ENCODER or JACKSON_ENCODER)
- **Internal Use**: Called by NatsPublisher publish methods to select encoding

---

### 3. CloudEventsHeaders (NEW)

**Responsibility**: Factory for generating and managing CloudEvents metadata headers.

**Location**: `runtime/src/main/java/org/mjelle/quarkus/easynats/CloudEventsHeaders.java`

**Type Parameters**: None (utility class with static methods and constants)

**Constants**:
```java
public static final String SPEC_VERSION = "1.0";
public static final String PREFIX = "ce-";
public static final String HEADER_SPECVERSION = "ce-specversion";
public static final String HEADER_TYPE = "ce-type";
public static final String HEADER_SOURCE = "ce-source";
public static final String HEADER_ID = "ce-id";
public static final String HEADER_TIME = "ce-time";
public static final String HEADER_DATACONTENTTYPE = "ce-datacontenttype";
public static final String CONTENT_TYPE_JSON = "application/json";
```

**Methods**:

#### `generateId(): String` (static)
- **Description**: Generate a unique event ID (UUID v4)
- **Returns**: UUID v4 as String (e.g., "550e8400-e29b-41d4-a716-446655440000")
- **Implementation**: `UUID.randomUUID().toString()`

#### `generateTime(): String` (static)
- **Description**: Generate current timestamp in ISO 8601 UTC format
- **Returns**: ISO 8601 timestamp (e.g., "2025-10-26T14:30:45.123456Z")
- **Implementation**: `Instant.now().toString()`

#### `generateType(Class<?> payloadClass): String` (static)
- **Description**: Generate CloudEvents type from fully-qualified class name
- **Parameters**: `payloadClass` - The payload type class
- **Returns**: Fully-qualified class name (e.g., "org.mjelle.quarkus.Order")
- **Implementation**: `payloadClass.getCanonicalName()`

#### `generateSource(): String` (static)
- **Description**: Generate CloudEvents source from hostname or application identifier
- **Returns**: Hostname or application name (e.g., "myapp-service" or "localhost")
- **Implementation**:
  - Try `InetAddress.getLocalHost().getHostName()`
  - Fallback to system property "app.name" if available
  - Fallback to "localhost" if all else fails

#### `createHeaders(T payload, String ceTypeOverride, String ceSourceOverride): io.nats.client.api.Headers`
- **Description**: Create NATS Headers object with all CloudEvents attributes
- **Parameters**:
  - `payload` - The object being published (used to auto-generate ce-type)
  - `ceTypeOverride` - Optional ce-type value (null → auto-generate)
  - `ceSourceOverride` - Optional ce-source value (null → auto-generate)
- **Returns**: `io.nats.client.api.Headers` with all ce-* attributes set
- **Generated Headers**:
  - `ce-specversion`: Always "1.0"
  - `ce-type`: ceTypeOverride OR auto-generated from payload.getClass().getCanonicalName()
  - `ce-source`: ceSourceOverride OR auto-generated via generateSource()
  - `ce-id`: Always auto-generated via generateId()
  - `ce-time`: Always auto-generated via generateTime()
  - `ce-datacontenttype`: Always "application/json"
- **Example**:
  ```java
  var headers = CloudEventsHeaders.createHeaders(
      myEvent,
      "com.example.UserCreated",  // ce-type override
      "/user-service"              // ce-source override
  );
  // Results in Headers with all 6 ce-* attributes populated
  ```

---

### 4. CloudEventsPayload (NEW)

**Responsibility**: Data structure representing a typed message with optional CloudEvents metadata.

**Location**: `runtime/src/main/java/org/mjelle/quarkus/easynats/CloudEventsPayload.java`

**Type Parameters**:
- `<T>`: Generic type parameter for the payload data

**Fields**:
| Field | Type | Visibility | Mutable | Description |
|-------|------|------------|---------|-------------|
| `data` | `T` | private | no | The actual event payload (any Java type) |
| `ceType` | `String` | private | no | CloudEvents type (optional, can be null for auto-generation) |
| `ceSource` | `String` | private | no | CloudEvents source (optional, can be null for auto-generation) |
| `ceId` | `String` | private | no | CloudEvents ID (auto-generated UUID v4) |
| `ceTime` | `String` | private | no | CloudEvents timestamp (auto-generated ISO 8601) |

**Constructor**:
```java
public CloudEventsPayload(T data, String ceType, String ceSource, String ceId, String ceTime) {
    this.data = Objects.requireNonNull(data, "Payload data cannot be null");
    this.ceType = ceType;
    this.ceSource = ceSource;
    this.ceId = Objects.requireNonNull(ceId, "ce-id cannot be null");
    this.ceTime = Objects.requireNonNull(ceTime, "ce-time cannot be null");
}
```

**Accessor Methods** (getters, no setters - immutable):
- `getData(): T`
- `getCeType(): String` (nullable)
- `getCeSource(): String` (nullable)
- `getCeId(): String` (non-null)
- `getCeTime(): String` (non-null)

**Design Notes**:
- **Immutable**: All fields final; no setters; thread-safe
- **Null Safety**: `data`, `ceId`, `ceTime` are non-null; `ceType` and `ceSource` can be null (auto-generated)
- **Purpose**: Represents a complete typed message with metadata before publishing to NATS
- **Internal Use**: Created during `publishCloudEvent()` calls to encapsulate payload + headers

---

## Architecture: Encoder/Decoder Resolution Chain

```
NatsPublisher<T>.publish(T payload)
│
├─→ Null check: if (payload == null) throw IllegalArgumentException("Cannot publish null object")
│
└─→ TypedPayloadEncoder.resolveEncoder(T.class)
    │
    ├─→ if canEncodeNatively(T.class) = true
    │   │
    │   └─→ TypedPayloadEncoder.encodeNatively(payload)
    │       (returns byte[] directly)
    │
    └─→ else canEncodeNatively(T.class) = false
        │
        └─→ TypedPayloadEncoder.encodeWithJackson(payload, mapper)
            (ObjectMapper configured via Quarkus injection)
            │
            └─→ on JsonProcessingException:
                throw SerializationException(user-friendly message)
```

### Priority Resolution Order

| Priority | Type Category | Examples | Handler | Encoding | Native Jackson Overhead |
|----------|---------------|----------|---------|----------|--------------------------|
| **1 (Highest)** | Primitive wrappers & String | Integer, Long, Double, String | `encodeNatively()` | UTF-8 string | None (direct string conversion) |
| **2** | Byte types | `byte`, `Byte`, `byte[]` | `encodeNatively()` | Base64-encoded string | None (never raw binary) |
| **3** | Primitive arrays | int[], long[], String[] | `encodeNatively()` | UTF-8 space/comma-separated | None (space/comma-separated) |
| **4 (Lowest)** | Complex types (POJOs, Records) | User domain classes, Collections | `encodeWithJackson()` | JSON (text-based) | Full Jackson serialization |

### Error Handling

| Scenario | Exception | Message Format | Handler |
|----------|-----------|-----------------|---------|
| Null payload | `IllegalArgumentException` | "Cannot publish null object" | NatsPublisher.publish() |
| Non-serializable (e.g., missing zero-arg constructor) | `SerializationException` | "Failed to serialize {ClassName}: {JsonProcessingException cause}" | TypedPayloadEncoder.encodeWithJackson() |
| Jackson not on classpath (complex types) | `SerializationException` | "Jackson ObjectMapper not available; cannot serialize complex type {ClassName}" | NatsPublisher initialization |

---

## Relationships & Interactions

```
┌─────────────────────────────────────────────────────────────┐
│                    NatsPublisher<T>                         │
├─────────────────────────────────────────────────────────────┤
│ - connectionManager: NatsConnectionManager                  │
│                                                              │
│ + publish(T): void                                          │
│   └─ uses TypedPayloadEncoder.resolveEncoder()             │
│                                                              │
│ + publishCloudEvent(T, String?, String?): void             │
│   ├─ uses TypedPayloadEncoder.resolveEncoder()             │
│   └─ uses CloudEventsHeaders.createHeaders()               │
└─────────────────────────────────────────────────────────────┘
         │                          │
         │ uses                     │ uses
         ▼                          ▼
┌──────────────────────┐    ┌──────────────────────┐
│ TypedPayloadEncoder  │    │ CloudEventsHeaders   │
├──────────────────────┤    ├──────────────────────┤
│ + canEncodeNatively()│    │ + generateId()       │
│ + encodeNatively()   │    │ + generateTime()     │
│ + encodeWithJackson()│    │ + generateType()     │
│ + resolveEncoder()   │    │ + generateSource()   │
└──────────────────────┘    │ + createHeaders()    │
                            └──────────────────────┘
                                     │ uses
                                     ▼
                            ┌──────────────────────┐
                            │CloudEventsPayload<T> │
                            ├──────────────────────┤
                            │ - data: T            │
                            │ - ceType: String     │
                            │ - ceSource: String   │
                            │ - ceId: String       │
                            │ - ceTime: String     │
                            │                      │
                            │ + getData()          │
                            │ + getCeType()        │
                            │ + getCeSource()      │
                            │ + getCeId()          │
                            │ + getCeTime()        │
                            └──────────────────────┘
```

---

## Implementation Notes

### Type Erasure Handling

Java generics are erased at runtime; `NatsPublisher<T>` cannot introspect the generic type `T` directly. **Solution**:
- Use `payload.getClass()` at runtime to determine the actual type
- This works because the payload object carries its concrete class information even if T is erased
- CloudEventsHeaders.generateType(payload.getClass()) uses this to auto-generate ce-type

### @RegisterForReflection Requirement

Complex types require `@RegisterForReflection` annotation for GraalVM native image compilation:

```java
@RegisterForReflection
public class UserCreatedEvent {
    private String userId;
    private String email;

    // Jackson-compatible constructor (required)
    public UserCreatedEvent() {}

    // Field constructor
    public UserCreatedEvent(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    // Getters/setters (required for Jackson)
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

**Documented in**: quickstart.md (Phase 1 artifact)

### Single Responsibility Principle

- **NatsPublisher**: Orchestrates publish/publishCloudEvent; handles null checks
- **TypedPayloadEncoder**: Owns encoder/decoder resolution logic
- **CloudEventsHeaders**: Owns CloudEvents metadata generation
- **CloudEventsPayload**: Immutable data structure; represents complete message

---

## Next Steps

- Implement these classes in runtime module
- Write unit tests for TypedPayloadEncoder and CloudEventsHeaders
- Create TypedPublisherResource REST endpoint for integration testing
- Document @RegisterForReflection usage in quickstart.md
