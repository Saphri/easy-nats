# Data Model: Basic NatsPublisher API

**Date**: 2025-10-25
**Feature**: Basic NatsPublisher (MVP)

## Entities

### NatsPublisher

**Purpose**: Simple injectable wrapper for publishing string messages to NATS JetStream.

**Type**: Runtime class (io.quarkus.easynats.NatsPublisher)

**Public API**:

```java
public class NatsPublisher {
    /**
     * Publishes a string message to the hardcoded NATS subject "test".
     *
     * @param message the raw message content to publish
     * @throws Exception if publication fails (connection error, broker unreachable, etc.)
     */
    public void publish(String message) throws Exception;
}
```

**Dependencies** (injected):
- `NatsConnectionManager` (singleton): provides access to the JetStream connection

**Lifecycle**:
- Injected via CDI (`@Inject NatsPublisher publisher`)
- Created once per application instance by Quarkus Arc container
- Lifecycle tied to application startup/shutdown

**State**: Stateless (all state managed by `NatsConnectionManager`)

---

### NatsConnectionManager

**Purpose**: Manages the singleton NATS JetStream connection for the entire application.

**Type**: Runtime class (io.quarkus.easynats.NatsConnectionManager)

**Scope**: Arc singleton (`@Singleton`)

**Public API**:

```java
@Singleton
public class NatsConnectionManager {
    /**
     * Returns the shared JetStream connection.
     * Initialized at application startup.
     * Do not close this connection; lifecycle is managed by Quarkus.
     *
     * @return the JetStream connection
     */
    public JetStream getJetStream();
}
```

**Dependencies**:
- `io.nats.client.Connection`: underlying NATS connection
- `io.nats.client.JetStream`: JetStream API wrapper
- Quarkus lifecycle events: `@Observes StartupEvent`, `@Observes ShutdownEvent`

**Initialization**:
- On `StartupEvent`: establish connection to NATS broker
- Connection URL: defaults to `nats://localhost:4222` (hardcoded for MVP; configurable in future)
- Fail fast: throw exception if unable to connect at startup

**Shutdown**:
- On `ShutdownEvent`: gracefully close JetStream and underlying Connection
- No-op if already closed

**State**:
- `connection`: io.nats.client.Connection instance
- `jetStream`: io.nats.client.JetStream instance
- `isConnected`: boolean flag (for health checks in future)

**Thread Safety**: JetStream API handles concurrent publish calls safely (via underlying connection pooling in JNats)

---

## Relationships

```
┌─────────────────────────────┐
│  Quarkus Application        │
│  (Arc Container)            │
└────────────┬────────────────┘
             │
             │ manages (lifecycle)
             ▼
┌─────────────────────────────┐
│ NatsConnectionManager       │
│ (@Singleton)                │
│                             │
│ - getJetStream()            │
└─────────────┬───────────────┘
              │
              │ delegates to
              ▼
┌──────────────────────────────────┐
│ JetStream (io.nats.client)       │
│ - publish(subject, payload)      │
└──────────────────────────────────┘
              ▲
              │ injected into
              │
┌─────────────────────────────┐
│  NatsPublisher              │
│                             │
│  - publish(String message)  │
└─────────────────────────────┘
              ▲
              │ injected via @Inject
              │
┌─────────────────────────────┐
│  User Application           │
│  (uses the extension)       │
└─────────────────────────────┘
```

---

## Message Flow (MVP)

**Scenario**: Developer publishes "hello" string to NATS.

```
User Application
    ↓
    [calls publisher.publish("hello")]
    ↓
NatsPublisher.publish("hello")
    ↓
    [retrieves JetStream from NatsConnectionManager singleton]
    ↓
    [calls jetStream.publish("test", "hello".getBytes())]
    ↓
JetStream API (io.nats.client)
    ↓
    [sends PUBLISH message to NATS broker on subject "test" with payload "hello"]
    ↓
NATS Broker (JetStream)
    ↓
    [stores in stream, acknowledges receipt]
    ↓
Broker returns PublishAck
    ↓
Message published successfully
```

---

## Data Types

### Input

| Field | Type | Constraints | Example |
|-------|------|-------------|---------|
| message | String | Non-null, any UTF-8 content | "hello" |

### Output

| Field | Type | Notes |
|-------|------|-------|
| (void) | PublishAck (internal) | Framework handles; developer sees no return value |

---

## Validation Rules

- **message parameter**: Must not be null (throws NullPointerException if caller passes null)
- **Connection state**: NatsConnectionManager MUST be connected before any publisher can be used
  - Verified at application startup (fail-fast)
  - If connection lost, next publish attempt will fail with IOException

---

## Constraints & Assumptions

- **Subject name**: Hardcoded to "test" (no configuration in MVP)
- **Message format**: Raw bytes (no CloudEvents wrapping)
- **Serialization**: String is converted to UTF-8 bytes for transmission
- **Broker location**: Hardcoded to `nats://localhost:4222` (configurable in future feature)
- **Stream configuration**: REQUIRED - Developer MUST manually create JetStream stream before publishing
  - Created via NATS CLI: `nats stream add test_stream --subjects test --discard old --max-age=-1 --replicas=1`
  - Stream name: `test_stream` (explicit naming convention)
  - Subject: `test` (matches publisher hardcoded subject)
  - See quickstart.md Step 0 for full setup instructions
  - Future v0.2 may add automatic stream creation if missing

---

## Future Extensions (Post-MVP)

These data model additions are anticipated for future features:

- **Typed generics**: `NatsPublisher<T>` with serialization support
- **Subject configuration**: `@NatsSubject` annotation to replace hardcoded "test"
- **Consumer management**: `NatsSubscriber` class with `@NatsSubscriber` annotation
- **CloudEvents support**: Message envelope with CloudEvents attributes in headers
- **Health checking**: NatsConnectionManager exposes readiness probe status
- **Observability**: Tracing context propagation via W3C Trace Context headers
