# Contract: Typed Publisher REST API

**Feature**: MVP 002 Typed NatsPublisher | **Date**: 2025-10-26 | **Phase**: Design Artifacts (Phase 1)

**Purpose**: Define REST endpoints for testing and demonstrating typed publishing and CloudEvents functionality in the integration test application.

---

## Endpoint: Publish Typed Object as JSON

### `POST /typed-publisher/publish`

**Summary**: Publish a typed domain object as JSON to NATS without CloudEvents headers.

**Request Body** (application/json):
```json
{
  "type": "string",
  "description": "The type of object to publish. Determines deserialization class.",
  "examples": ["java.lang.String", "java.lang.Integer", "org.mjelle.Order", "org.mjelle.UserCreatedEvent"]
}
```

**Request Body Schema**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `objectType` | string | yes | Fully-qualified Java class name (e.g., "java.lang.String", "org.example.Order") |
| `payload` | object | yes | The object to publish (structure varies by objectType) |

**Example Requests**:

**1. Publish a String**:
```bash
curl -X POST http://localhost:8080/typed-publisher/publish \
  -H "Content-Type: application/json" \
  -d '{
    "objectType": "java.lang.String",
    "payload": "hello world"
  }'
```

**2. Publish an Integer**:
```bash
curl -X POST http://localhost:8080/typed-publisher/publish \
  -H "Content-Type: application/json" \
  -d '{
    "objectType": "java.lang.Integer",
    "payload": 42
  }'
```

**3. Publish a custom domain object**:
```bash
curl -X POST http://localhost:8080/typed-publisher/publish \
  -H "Content-Type: application/json" \
  -d '{
    "objectType": "org.mjelle.quarkus.easynats.it.Order",
    "payload": {
      "orderId": "ORD-123",
      "customerEmail": "alice@example.com",
      "amount": 99.99
    }
  }'
```

**Response** (200 OK):
```json
{
  "status": "published",
  "objectType": "org.mjelle.quarkus.easynats.it.Order",
  "subject": "test",
  "message": "Order message published successfully to NATS subject 'test'"
}
```

**Response Headers**:
- `Content-Type: application/json`

**Status Codes**:
| Code | Reason | Body |
|------|--------|------|
| 200 | Success | PublishResult JSON |
| 400 | Invalid request (null payload, unsupported type) | ErrorResponse JSON |
| 500 | Serialization error (Jackson failure) | ErrorResponse JSON |

**Error Response** (400/500):
```json
{
  "status": "error",
  "objectType": "org.example.MyClass",
  "subject": "test",
  "error": "Cannot publish null object"
}
```

**Implementation Notes**:
- Endpoint is defined in `TypedPublisherResource.java`
- Uses `NatsPublisher<T>` internally with reflection to instantiate the appropriate generic type
- Supports primitive types (String, Integer, Long, etc.) and complex POJOs
- Error messages are propagated from `TypedPayloadEncoder` and `NatsPublisher.publish()`

---

## Endpoint: Publish with CloudEvents Metadata

### `POST /typed-publisher/publish-cloudevents`

**Summary**: Publish a typed object with CloudEvents metadata headers (ce-type, ce-source, ce-id, ce-time).

**Request Body** (application/json):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `objectType` | string | yes | Fully-qualified Java class name |
| `payload` | object | yes | The object to publish |
| `ceType` | string | no | CloudEvents type (default: auto-generated from class name) |
| `ceSource` | string | no | CloudEvents source (default: auto-generated from hostname) |

**Example Request**:

**1. Publish domain object with CloudEvents (explicit metadata)**:
```bash
curl -X POST http://localhost:8080/typed-publisher/publish-cloudevents \
  -H "Content-Type: application/json" \
  -d '{
    "objectType": "org.mjelle.quarkus.easynats.it.Order",
    "payload": {
      "orderId": "ORD-456",
      "customerEmail": "bob@example.com",
      "amount": 149.99
    },
    "ceType": "com.example.OrderPlaced",
    "ceSource": "/order-service"
  }'
```

**2. Publish with auto-generated CloudEvents metadata**:
```bash
curl -X POST http://localhost:8080/typed-publisher/publish-cloudevents \
  -H "Content-Type: application/json" \
  -d '{
    "objectType": "org.mjelle.quarkus.easynats.it.Order",
    "payload": {
      "orderId": "ORD-789",
      "customerEmail": "charlie@example.com",
      "amount": 199.99
    }
  }'
```

**Response** (200 OK):
```json
{
  "status": "published",
  "objectType": "org.mjelle.quarkus.easynats.it.Order",
  "subject": "test",
  "ceType": "com.example.OrderPlaced",
  "ceSource": "/order-service",
  "ceId": "550e8400-e29b-41d4-a716-446655440000",
  "ceTime": "2025-10-26T14:30:45.123456Z",
  "message": "Order message published successfully with CloudEvents headers"
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `status` | string | Always "published" on success |
| `objectType` | string | The type that was published |
| `subject` | string | NATS subject used (hardcoded "test") |
| `ceType` | string | CloudEvents type (provided or auto-generated) |
| `ceSource` | string | CloudEvents source (provided or auto-generated) |
| `ceId` | string | CloudEvents ID (always auto-generated UUID) |
| `ceTime` | string | CloudEvents timestamp (always auto-generated ISO 8601) |
| `message` | string | Success message |

**Status Codes**:
| Code | Reason | Body |
|------|--------|------|
| 200 | Success | PublishCloudEventsResult JSON |
| 400 | Invalid request | ErrorResponse JSON |
| 500 | Serialization error | ErrorResponse JSON |

**Error Response** (400/500):
```json
{
  "status": "error",
  "objectType": "org.example.MyClass",
  "subject": "test",
  "error": "Failed to serialize Order: missing zero-arg constructor"
}
```

**CloudEvents Headers Set on Message**:
When published to NATS, the message will have these headers (accessible via NATS headers API):

```
ce-specversion: 1.0
ce-type: com.example.OrderPlaced (or auto-generated from objectType)
ce-source: /order-service (or auto-generated from hostname)
ce-id: 550e8400-e29b-41d4-a716-446655440000 (auto-generated UUID)
ce-time: 2025-10-26T14:30:45.123456Z (auto-generated ISO 8601)
ce-datacontenttype: application/json
```

**Implementation Notes**:
- Endpoint is defined in `TypedPublisherResource.java`
- Uses `NatsPublisher<T>.publishCloudEvent()` internally
- CloudEvents headers are generated by `CloudEventsHeaders` factory
- Supports same type range as `/publish` endpoint
- If ceType or ceSource are not provided, auto-generation occurs:
  - `ceType` defaults to fully-qualified class name (e.g., "org.mjelle.quarkus.easynats.it.Order")
  - `ceSource` defaults to hostname or "localhost"

---

## Data Models (Request/Response)

### PublishRequest

```java
public record PublishRequest(
    String objectType,
    Object payload
) {}
```

### PublishCloudEventsRequest

```java
public record PublishCloudEventsRequest(
    String objectType,
    Object payload,
    String ceType,      // nullable - auto-generated if not provided
    String ceSource     // nullable - auto-generated if not provided
) {}
```

### PublishResult

```java
public record PublishResult(
    String status,           // "published"
    String objectType,       // fully-qualified class name
    String subject,          // "test"
    String message           // descriptive success message
) {}
```

### PublishCloudEventsResult

```java
public record PublishCloudEventsResult(
    String status,           // "published"
    String objectType,       // fully-qualified class name
    String subject,          // "test"
    String ceType,           // CloudEvents type (provided or auto-generated)
    String ceSource,         // CloudEvents source (provided or auto-generated)
    String ceId,             // CloudEvents ID (auto-generated UUID)
    String ceTime,           // CloudEvents timestamp (auto-generated ISO 8601)
    String message           // descriptive success message
) {}
```

### ErrorResponse

```java
public record ErrorResponse(
    String status,           // "error"
    String objectType,       // the type that failed
    String subject,          // "test"
    String error             // exception message
) {}
```

---

## Implementation Class: TypedPublisherResource

**Location**: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/TypedPublisherResource.java`

**Annotations**:
- `@Path("/typed-publisher")` - Base path for all endpoints
- `@Singleton` - CDI singleton scope
- `@Produces(MediaType.APPLICATION_JSON)` - All responses are JSON
- `@Consumes(MediaType.APPLICATION_JSON)` - All requests are JSON

**Dependencies** (constructor-injected):
- `NatsPublisher<?>` - Generic publisher for type-safe publishing
- `ObjectMapper` (optional) - For JSON serialization of responses

**Key Methods**:
1. `publish(PublishRequest request): PublishResult` - Handles `POST /typed-publisher/publish`
2. `publishCloudEvents(PublishCloudEventsRequest request): PublishCloudEventsResult` - Handles `POST /typed-publisher/publish-cloudevents`

**Error Handling**:
- Catch `IllegalArgumentException` → Return 400 with ErrorResponse
- Catch `SerializationException` → Return 500 with ErrorResponse
- Catch all other exceptions → Return 500 with ErrorResponse

---

## Testing Strategy

**Manual Testing** (via docker-compose + NATS CLI):

1. **Test Typed Publishing**:
   ```bash
   # In terminal 1, subscribe to messages
   nats sub test

   # In terminal 2, publish via REST
   curl -X POST http://localhost:8080/typed-publisher/publish \
     -H "Content-Type: application/json" \
     -d '{"objectType":"java.lang.String","payload":"hello"}'

   # Expected output in terminal 1:
   # [1] Received on "test": hello
   ```

2. **Test CloudEvents Publishing**:
   ```bash
   # In terminal 1, subscribe with header inspection
   nats sub test --raw

   # In terminal 2, publish via REST with CloudEvents
   curl -X POST http://localhost:8080/typed-publisher/publish-cloudevents \
     -H "Content-Type: application/json" \
     -d '{"objectType":"java.lang.String","payload":"world"}'

   # Expected: Message with ce-* headers visible in NATS CLI output
   ```

3. **Test Error Handling**:
   ```bash
   # Publish null object
   curl -X POST http://localhost:8080/typed-publisher/publish \
     -H "Content-Type: application/json" \
     -d '{"objectType":"java.lang.String","payload":null}'

   # Expected: 400 response with error message
   ```

---

## Notes

- Both endpoints publish to the hardcoded subject "test"
- Type resolution uses fully-qualified class names for flexibility
- CloudEvents spec version is always "1.0" (binary content mode)
- Endpoints are test/demo only; production would use direct injection of `NatsPublisher<MyType>`
