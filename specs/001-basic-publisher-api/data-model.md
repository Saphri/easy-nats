# Phase 1 Data Model: Basic NatsPublisher API (MVP)

**Branch**: `001-basic-publisher-api`
**Status**: Complete
**Date**: 2025-10-26

## Overview

This document defines the domain entities, relationships, and lifecycle for MVP 001. All entities are simple; no complex state machines or persistence required.

---

## Domain Entities

### NatsConnectionManager (Singleton Lifecycle Bean)

**Purpose**: Manages the single shared NATS JetStream connection for the entire Quarkus application.

**Scope**: `@Singleton` (exactly one instance per application)

**Fields**:
| Field | Type | Nullable | Constraints | Notes |
|-------|------|----------|-------------|-------|
| `jetStream` | `JetStream` | No | Not null after initialization | Shared JetStream instance; created on StartupEvent |
| `connection` | `NatsConnection` | No | Not null after initialization | Underlying NATS connection (internal, not exposed) |
| `connectionUrl` | `String` (hardcoded) | No | Value: `nats://localhost:4222` | MVP 001 only; configuration deferred to MVP 002+ |
| `username` | `String` (hardcoded) | No | Value: `guest` | MVP 001 only; configuration deferred to MVP 002+ |
| `password` | `String` (hardcoded) | No | Value: `guest` | MVP 001 only; configuration deferred to MVP 002+ |

**Lifecycle**:
```
[Quarkus Application Started]
  ↓
[@Observes StartupEvent fires]
  ↓
[Initialize NATS connection synchronously]
  ↓
[If connection fails: throw IOException, abort startup]
  ↓
[Connection ready; getJetStream() available for injection]
```

**Methods**:
- `public void onStartup(@Observes StartupEvent event) throws IOException`
  - Synchronously initializes connection on Quarkus startup
  - Throws `IOException` if broker unreachable (fail-fast)
  - Completes before any `@Inject` fields are populated (Quarkus guarantee)

- `public JetStream getJetStream()`
  - Returns shared JetStream instance
  - Safe to call after initialization complete
  - Never returns null

**Validation Rules**:
- Connection parameters hardcoded (no validation needed for MVP 001)
- Connection must be established before any NatsPublisher injection
- Exactly one instance per application (enforced by `@Singleton`)

**State Transitions**:
| State | Trigger | Next State |
|-------|---------|-----------|
| UNINITIALIZED | Quarkus startup | INITIALIZING |
| INITIALIZING | Connection established | READY |
| INITIALIZING | IOException thrown | FAILED (application startup aborted) |
| READY | (terminal) | N/A |

---

### NatsPublisher (Singleton Facade Bean)

**Purpose**: Simple wrapper providing `publish()` method for sending string messages to NATS JetStream.

**Scope**: `@Singleton` (exactly one instance per application)

**Fields**:
| Field | Type | Nullable | Constraints | Notes |
|-------|------|----------|-------------|-------|
| `connectionManager` | `NatsConnectionManager` | No | Arc dependency injection | Injected; initialized before NatsPublisher (timing guarantee) |
| `subject` | `String` (hardcoded) | No | Value: `"test"` | MVP 001 only; subject configuration deferred to MVP 002+ |
| `stream` | `String` (hardcoded) | No | Value: `"test_stream"` | JetStream stream name; must exist before startup |

**Methods**:
- `public void publish(String message) throws Exception`
  - **Parameters**: `message` (UTF-8 string to publish)
  - **Return**: `void` (fire-and-forget model)
  - **Throws**: Checked `Exception` (IOException on network error, auth failure, broker down)
  - **Behavior**:
    1. Obtain JetStream instance from NatsConnectionManager
    2. Encode message to UTF-8 bytes
    3. Create PublishOptions (none for MVP; defaults used)
    4. Call `jetStream.publish(subject, messageBytes)`
    5. Return (or throw if publish fails)
  - **Timing**: Blocks until NATS broker acknowledges (no async in MVP 001)

**Validation Rules**:
- `message` parameter: Non-null string (no length limit for MVP)
- `subject` and `stream` hardcoded (no user configuration)
- Caller responsible for exception handling (checked Exception contract)

**Dependencies**:
- Depends on: `NatsConnectionManager` (Arc injection)
- Used by: Application code (via `@Inject` annotation)

---

## Data Flow Diagrams

### Application Startup Sequence

```
┌─────────────────────────────────────────────────────────────┐
│ Quarkus Application Starts                                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ↓
        ┌──────────────────────────────────┐
        │ @Observes StartupEvent fires     │
        │ (NatsConnectionManager)           │
        └──────────────────┬───────────────┘
                           │
              ┌────────────┴────────────┐
              ↓                         ↓
    ┌──────────────────┐    ┌──────────────────┐
    │ Connect to NATS  │    │ Broker DOWN      │
    │ nats://loc:4222  │    │ (IOException)    │
    └────────┬─────────┘    └────────┬─────────┘
             │                      │
             ↓                      ↓
    ┌──────────────────┐    ┌──────────────────┐
    │ Connection READY │    │ Startup ABORTS   │
    │ (Singleton)      │    │ (Fail-fast)      │
    └────────┬─────────┘    └──────────────────┘
             │
             ↓
    ┌──────────────────────────────────┐
    │ Arc injects NatsPublisher beans   │
    │ (Timing guaranteed; conn ready)   │
    └──────────────┬───────────────────┘
                   │
                   ↓
         ┌────────────────────┐
         │ App Ready to Use   │
         │ @Inject Publishers │
         └────────────────────┘
```

### Publish Message Sequence

```
┌────────────────────────────────────┐
│ Application Code                   │
│ publisher.publish("hello")         │
└─────────────────┬──────────────────┘
                  │
                  ↓
    ┌─────────────────────────────────┐
    │ NatsPublisher.publish()          │
    │ - Get JetStream from Manager     │
    │ - Encode "hello" → UTF-8 bytes   │
    │ - Call jetStream.publish(...)    │
    └─────────────┬───────────────────┘
                  │
        ┌─────────┴──────────┐
        ↓                    ↓
  ┌─────────────┐    ┌───────────────┐
  │ NATS ACK    │    │ IOException   │
  │ (Success)   │    │ (Throw)       │
  └─────────────┘    └───────────────┘
        │                    │
        ↓                    ↓
  ┌─────────────┐    ┌───────────────┐
  │ Return void │    │ Caller must   │
  │ (completed) │    │ catch & handle│
  └─────────────┘    └───────────────┘
```

---

## Relationships & Constraints

### Dependency Graph

```
Application
    ↓
@Inject NatsPublisher
    ↓
NatsPublisher
    ↓ (injected)
NatsConnectionManager
    ↓ (initializes on)
StartupEvent
```

### Design Constraints

1. **Single Connection Per App**: Exactly one NatsConnectionManager instance (enforced by `@Singleton`)
2. **Timing Guarantee**: NatsConnectionManager initializes synchronously before NatsPublisher injection
3. **Fail-Fast on Startup**: Connection failure aborts application immediately (IOException, no retries)
4. **No Configuration**: All connection parameters hardcoded for MVP 001 (deferred to MVP 002+)
5. **Checked Exceptions**: publish() throws Exception; caller must handle explicitly

---

## Validation & Error Handling

### Startup Validation

| Scenario | Action |
|----------|--------|
| NATS broker at `nats://localhost:4222` reachable | Create connection; proceed |
| NATS broker unreachable (connection refused) | Throw IOException; abort startup |
| Authentication fails (guest/guest invalid) | Throw IOException; abort startup |

### Publish Validation

| Scenario | Action |
|----------|--------|
| Message publish succeeds | Return void normally |
| Network error during publish | Throw IOException; propagate to caller |
| Stream `test_stream` doesn't exist | JetStream throws; propagate as IOException |
| Subject `test` not in stream | JetStream throws; propagate as IOException |

---

## Implementation Notes

### NatsConnectionManager Implementation Details

```java
@Singleton
public class NatsConnectionManager {
    private final JetStream jetStream;

    NatsConnectionManager() throws IOException {
        // Initialize connection in constructor; Quarkus will call this before StartupEvent
        NatsConnection conn = Nats.connect("nats://localhost:4222");
        // Auth is implicit in connection URL (format: nats://username:password@host:port)
        this.jetStream = conn.jetStream();
    }

    public JetStream getJetStream() {
        return jetStream;
    }
}
```

**Note**: Constructor injection is used. If StartupEvent hook is needed, use `@Observes StartupEvent event` as a method parameter in another bean, or rely on constructor initialization (preferred).

### NatsPublisher Implementation Details

```java
@Singleton
public class NatsPublisher {
    private final NatsConnectionManager connectionManager;

    private static final String SUBJECT = "test";

    // Constructor injection (REQUIRED; never use @Inject field injection)
    NatsPublisher(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void publish(String message) throws Exception {
        JetStream js = connectionManager.getJetStream();
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        js.publish(SUBJECT, data);
    }
}
```

**Note**: All dependencies injected via constructor. This enables immutability and testability. For tests only, `@Inject` field injection is permitted.

---

## Future Considerations (MVP 002+)

- **Configuration Properties**: Move hardcoded values to `application.properties`
- **Typed Generics**: `NatsPublisher<T>` with serialization support
- **Annotations**: `@NatsSubject("subject-name")` for subject specification
- **Async Publishing**: `publishAsync(String): CompletionStage<PublishAck>`
- **Message Lifecycle**: Hooks for pre-publish validation, post-publish callbacks
