# Contract: Typed Publisher REST API

**Feature**: MVP 002 Typed NatsPublisher | **Date**: 2025-10-26 | **Phase**: Design Artifacts (Phase 1)

**Purpose**: Define REST endpoints for testing and demonstrating type-safe publishing and CloudEvents functionality in the integration test application.

---

## Overview

The Typed Publisher API uses **path-based endpoint separation** to enforce type safety at the HTTP boundary. Each endpoint is explicitly typed for a specific payload type (String, TestOrder), eliminating runtime type dispatching logic.

**Base Path**: `/typed-publisher`

**Content Type**: `application/json`

---

## Endpoint: Publish String Payload

### `POST /typed-publisher/string`

**Summary**: Publish a String message to NATS without CloudEvents headers.

**Request Body**: Plain JSON string

```json
"hello world"
```

**Example Request**:
```bash
curl -X POST http://localhost:8081/typed-publisher/string \
  -H "Content-Type: application/json" \
  -d '"hello world"'
```

**Response** (204 No Content):
- No response body
- Message published successfully to NATS subject "test"

**Status Codes**:
| Code | Reason | Body |
|------|--------|------|
| 204 | Success | (empty) |
| 400 | Bad Request (null message) | Error message string |
| 500 | Internal Server Error | Error message string |

---

## Endpoint: Publish TestOrder Payload

### `POST /typed-publisher/order`

**Summary**: Publish a TestOrder domain object to NATS without CloudEvents headers.

**Request Body**: TestOrder JSON object

```json
{
  "orderId": "ORD-123",
  "amount": 99
}
```

**TestOrder Schema**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `orderId` | string | yes | Order identifier (e.g., "ORD-123") |
| `amount` | integer | yes | Order amount in cents (e.g., 99) |

**Example Request**:
```bash
curl -X POST http://localhost:8081/typed-publisher/order \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-456",
    "amount": 150
  }'
```

**Response** (204 No Content):
- No response body
- Order published successfully to NATS subject "test"

**Status Codes**:
| Code | Reason | Body |
|------|--------|------|
| 204 | Success | (empty) |
| 400 | Bad Request (null order) | Error message string |
| 500 | Internal Server Error | Error message string |

---

## Endpoint: Publish String with CloudEvents

### `POST /typed-publisher/string-cloudevents`

**Summary**: Publish a String message with CloudEvents 1.0 metadata headers (ce-type, ce-source, ce-id, ce-time).

**Request Body**: Plain JSON string

```json
"hello world"
```

**Example Request**:
```bash
curl -X POST http://localhost:8081/typed-publisher/string-cloudevents \
  -H "Content-Type: application/json" \
  -d '"hello world"'
```

**Response** (200 OK):
```json
{
  "ceType": "java.lang.String",
  "ceSource": "localhost",
  "ceId": "550e8400-e29b-41d4-a716-446655440000",
  "ceTime": "2025-10-26T14:30:45.123456Z"
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `ceType` | string | CloudEvents type (auto-generated from class name) |
| `ceSource` | string | CloudEvents source (auto-generated from hostname) |
| `ceId` | string | CloudEvents ID (auto-generated UUID) |
| `ceTime` | string | CloudEvents timestamp (auto-generated ISO 8601) |

**Status Codes**:
| Code | Reason | Body |
|------|--------|------|
| 200 | Success | CloudEventsMetadata JSON |
| 400 | Bad Request (null message) | Error message string |
| 500 | Internal Server Error | Error message string |

**CloudEvents Headers Sent to NATS**:
```
ce-specversion: 1.0
ce-type: java.lang.String
ce-source: localhost
ce-id: 550e8400-e29b-41d4-a716-446655440000
ce-time: 2025-10-26T14:30:45.123456Z
ce-datacontenttype: application/json
```

---

## Endpoint: Publish TestOrder with CloudEvents

### `POST /typed-publisher/order-cloudevents`

**Summary**: Publish a TestOrder domain object with CloudEvents 1.0 metadata headers.

**Request Body**: TestOrder JSON object

```json
{
  "orderId": "ORD-789",
  "amount": 200
}
```

**Example Request**:
```bash
curl -X POST http://localhost:8081/typed-publisher/order-cloudevents \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-789",
    "amount": 200
  }'
```

**Response** (200 OK):
```json
{
  "ceType": "org.mjelle.quarkus.easynats.it.TestOrder",
  "ceSource": "localhost",
  "ceId": "660e8400-e29b-41d4-a716-446655440001",
  "ceTime": "2025-10-26T14:35:50.654321Z"
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `ceType` | string | CloudEvents type (auto-generated from class name) |
| `ceSource` | string | CloudEvents source (auto-generated from hostname) |
| `ceId` | string | CloudEvents ID (auto-generated UUID) |
| `ceTime` | string | CloudEvents timestamp (auto-generated ISO 8601) |

**Status Codes**:
| Code | Reason | Body |
|------|--------|------|
| 200 | Success | CloudEventsMetadata JSON |
| 400 | Bad Request (null order) | Error message string |
| 500 | Internal Server Error | Error message string |

**CloudEvents Headers Sent to NATS**:
```
ce-specversion: 1.0
ce-type: org.mjelle.quarkus.easynats.it.TestOrder
ce-source: localhost
ce-id: 660e8400-e29b-41d4-a716-446655440001
ce-time: 2025-10-26T14:35:50.654321Z
ce-datacontenttype: application/json
```

---

## Implementation Details

**Location**: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/TypedPublisherResource.java`

**Dependencies** (constructor-injected):
- `NatsConnectionManager` - Manages NATS connection and JetStream access
- `ObjectMapper` - Jackson ObjectMapper for JSON serialization

**Design Rationale**:

1. **Path-Based Type Safety**: Each endpoint is explicitly typed for a specific payload type (String or TestOrder), eliminating runtime type dispatching logic and providing compile-time type safety.

2. **HTTP Status Codes**: Endpoints return proper REST status codes:
   - `204 No Content` for simple publish (payload only, no response body needed)
   - `200 OK` with CloudEvents metadata for verification
   - `400 Bad Request` for validation errors
   - `500 Internal Server Error` for serialization/connection errors

3. **Actual CloudEvents Metadata**: CloudEvents endpoints return the **actual** metadata that was published (same ce-id and ce-time sent to NATS), not newly generated values. This ensures API responses accurately represent the published state.

4. **Generic Type Safety**: Each endpoint creates a properly typed `NatsPublisher<T>` at runtime:
   - `/string` → `NatsPublisher<String>`
   - `/order` → `NatsPublisher<TestOrder>`

---

## Error Handling

All endpoints follow consistent error handling:

**400 Bad Request** (null payload):
```
Message cannot be null
```

**500 Internal Server Error** (serialization failure):
```
Failed to publish: [error details]
```

---

## Testing Strategy

**Integration Tests** (in `TypedPublisherTest.java`):

1. Test String publishing (204 No Content)
2. Test TestOrder publishing (204 No Content)
3. Test message delivery on NATS broker
4. Test String CloudEvents (200 OK with metadata)
5. Test TestOrder CloudEvents (200 OK with metadata)

**Manual Testing**:
```bash
# Publish String
curl -X POST http://localhost:8081/typed-publisher/string \
  -H "Content-Type: application/json" \
  -d '"test message"'

# Publish Order
curl -X POST http://localhost:8081/typed-publisher/order \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-100","amount":500}'

# Publish String with CloudEvents
curl -X POST http://localhost:8081/typed-publisher/string-cloudevents \
  -H "Content-Type: application/json" \
  -d '"test message"'

# Publish Order with CloudEvents
curl -X POST http://localhost:8081/typed-publisher/order-cloudevents \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-200","amount":1000}'
```

---

## Notes

- All endpoints publish to the hardcoded NATS subject "test"
- CloudEvents spec version is always "1.0"
- Type-safe endpoints prevent runtime type dispatch errors
- Response metadata in CloudEvents endpoints matches NATS headers exactly
- Endpoints demonstrate type-safe publishing; production code would use direct generic type injection
