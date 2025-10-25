# API Contract: NatsPublisher

**Feature**: Basic NatsPublisher (MVP)
**Version**: 0.1.0
**Date**: 2025-10-25

## Overview

The NatsPublisher is a simple CDI injectable class that provides a synchronous API for publishing string messages to NATS JetStream.

**Important**: This is NOT a REST/HTTP endpoint. The publisher is injected directly into user application code.

---

## Public Interface

### Class: `io.quarkus.easynats.NatsPublisher`

#### Method: `publish(String message)`

**Signature**:
```java
public void publish(String message) throws Exception
```

**Purpose**: Publish a string message to the hardcoded NATS subject "test".

**Parameters**:
- `message` (String): The message payload. Non-null required.

**Return Value**: void (no return value)

**Exceptions Thrown**:
- `NullPointerException`: If `message` is null
- `IOException`: If NATS connection is lost or broker is unreachable
- `JetStreamApiException`: If JetStream API call fails (e.g., stream not available)
- `InterruptedException`: If the thread is interrupted during publish

**Side Effects**:
- Message is sent to NATS broker on subject "test"
- Broker stores the message in its configured stream

**Thread Safety**: Safe to call concurrently from multiple threads. The underlying JetStream API handles synchronization.

**Performance**:
- Expected latency: <100ms for local NATS broker
- Synchronous: method blocks until message is published or error occurs

---

## Injection Contract

### Qualifier: CDI `@Inject`

**Usage in User Code**:
```java
@Inject
NatsPublisher publisher;

// Later in code:
publisher.publish("hello");
```

**Scope**: Singleton per Quarkus application instance

**Lifecycle**:
- Created at Quarkus application startup
- Destroyed at application shutdown
- One instance shared across entire application

---

## Error Handling

| Scenario | Exception | Cause | Recovery |
|----------|-----------|-------|----------|
| Null message passed | NullPointerException | Caller error | Pass non-null message |
| NATS broker unreachable | IOException | Connection lost | Retry after broker is available |
| Stream missing in broker | JetStreamApiException | Configuration error | Configure stream in NATS broker |
| Thread interrupted | InterruptedException | Concurrent shutdown | Retry or propagate |

**Note**: MVP does not implement automatic retry logic. User application is responsible for retry strategies.

---

## Contract Version & Stability

**Version**: 0.1.0 (MVP)

**Stability**: UNSTABLE
- This MVP is expected to change in future releases
- Planned additions: type generics, subject configuration, CloudEvents support
- Breaking changes may occur until v1.0.0 stable release

**Deprecation Plan**:
- When `NatsPublisher<T>` (generic) is released, the untyped `NatsPublisher` will be marked `@Deprecated`
- Untyped version will remain usable for 2+ releases for backward compatibility

---

## Integration Testing Contract

### Test Scenario: Basic Publish

**Setup**:
- Local NATS broker running (Docker Compose via `integration-tests/src/test/resources/docker-compose.yml`)
- Quarkus test application with EasyNATS extension loaded
- `NatsPublisher` injected

**Test Steps**:
1. Inject `NatsPublisher`
2. Call `publisher.publish("hello")`
3. Wait for message to arrive on NATS broker subject "test"
4. Assert message content == "hello"

**Expected Result**: PASS - Message is successfully published and received

---

## Backward Compatibility Notes

- **Java Version**: Requires Java 21+ (per Principle IV)
- **Quarkus Version**: Requires Quarkus 3.27.0+
- **NATS Broker**: Tested with NATS 2.9+ (with JetStream enabled)

---

## Future Extensions (Not in MVP)

These contracts will be added in future releases:

- **Typed generics**: `NatsPublisher<T>` with `publish(T message)`
- **Subject configuration**: `@NatsSubject("custom-subject")` annotation
- **Async publishing**: `publishAsync(String message) -> CompletableFuture<Void>`
- **Subscriber contract**: `NatsSubscriber` class with method-level `@NatsSubscriber` annotation
