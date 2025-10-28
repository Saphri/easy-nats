# Data Model: NATS Connection Access

**Feature**: 010-nats-connection-access | **Date**: 2025-10-28

## Entity: NatsConnection

**Purpose**: CDI-injectable singleton wrapper around the underlying NATS connection. Implements `AutoCloseable` for try-with-resources support while preventing accidental closure.

**Package**: `org.mjelle.quarkus.easynats` (runtime module)

### Class Definition

```java
public class NatsConnection implements AutoCloseable {
    private final io.nats.client.Connection delegate;

    // Constructor (package-private; registered by CDI processor)
    NatsConnection(io.nats.client.Connection delegate);

    // Core delegation methods (full NATS API)
    // ... publish, subscribe, createJetStreamContext, getServerInfo, etc.

    // AutoCloseable implementation (no-op)
    @Override
    public void close();
}
```

### Key Fields

| Field | Type | Access | Description |
|-------|------|--------|-------------|
| `delegate` | `io.nats.client.Connection` | Private, final | Underlying jnats connection; never null |

### Key Methods (Delegation)

**Publishing**:
- `publish(String subject, byte[] data): void`
- `publish(String subject, Headers headers, byte[] data): void`
- All variants from jnats `Connection` interface

**Subscription**:
- `subscribe(String subject): Subscription`
- `createDispatcher(MessageHandler): Dispatcher`
- All variants from jnats `Connection` interface

**JetStream**:
- `createJetStreamContext(): JetStreamContext`
- `createJetStreamContext(JetStreamOptions): JetStreamContext`

**Advanced**:
- `getServerInfo(): ServerInfo`
- `setConnectionListener(ConnectionListener): void`
- `close(): void` (no-op; does NOT close delegate)
- All listener registration methods
- `keyValue()` for key-value store access
- `keyValueManagement()` for KV bucket management
- All other `Connection` interface methods

### Constructor Semantics

- **Visibility**: Package-private (only CDI processor instantiates)
- **Parameters**: Single parameter of type `io.nats.client.Connection`
- **Validation**: Fails immediately if delegate is null or closed
- **Throws**: `NatsConnectionException` with clear message if connection invalid
- **Immutability**: `delegate` field is final; no state changes possible

### close() Method Behavior

**Method Signature**: `public void close() throws IOException` (implements `AutoCloseable`)

**Behavior**:
- **Does NOT close the underlying connection**
- **Is a safe no-op** that returns immediately
- **Never throws exception** (IOException not actually thrown)
- **Allows safe try-with-resources usage** without side effects

**Rationale**: Try-with-resources expects `AutoCloseable`. By implementing it with a no-op close, developers can use standard Java patterns safely while preventing accidental connection closure that would break other subscribers/publishers.

### Thread Safety

- **Guaranteed by delegation**: All thread-safety guarantees come from underlying jnats `Connection`
- **Wrapper does not add synchronization**: Pure transparent delegation
- **Testing scope**: Verify wrapper doesn't introduce bottlenecks or state corruption

### Lifecycle

**Creation**:
- Instantiated once during Quarkus startup
- Created by `QuarkusEasyNatsProcessor` (build-time processor)
- Registered as `@Singleton` CDI bean
- Connection is already established before wrapper is created

**Availability**:
- Registered in CDI context
- Available for injection into any bean (after initialization completes)
- Pre-initialization injection causes fail-fast error

**Destruction**:
- Lifecycle tied to Quarkus application shutdown
- NOT destroyed by calling `close()` on wrapper (no-op)
- Actual connection lifecycle managed by extension's connection manager
- Connection closed only when Quarkus app shuts down

### Validation Rules

1. **Delegate never null**: Constructor validation ensures delegate is initialized
2. **Delegate never closed**: Wrapper never calls `close()` on delegate
3. **Close() is safe**: Calling `close()` on wrapper has no side effects
4. **Delegation is transparent**: All method calls forward to delegate without modification
5. **No connection reuse**: Wrapper always wraps the same singleton connection (1:1 relationship)

### Configuration Integration

Connection is configured via Quarkus properties before wrapper is created:
- `quarkus.easynats.servers` → passed to `Nats.connect(options)`
- `quarkus.easynats.username` → passed to `Options.username()`
- `quarkus.easynats.password` → passed to `Options.password()`
- `quarkus.easynats.ssl-enabled` → injects default `SSLContext` if true

Wrapper assumes connection is already fully configured and connected; it does not handle configuration.

---

## Entity: NatsConnectionProvider (Internal CDI Bean)

**Purpose**: Internal CDI-scoped provider that manages the actual `io.nats.client.Connection` lifecycle. Registered by build processor. Developers do NOT directly use this; they inject `NatsConnection`.

**Package**: `org.mjelle.quarkus.easynats.runtime` (internal package)

### Class Definition

```java
@Singleton  // CDI scope
class NatsConnectionProvider {
    private final io.nats.client.Connection connection;

    // Constructor (instantiated by Quarkus CDI)
    NatsConnectionProvider(NatsConfiguration config);

    // Get the underlying connection
    io.nats.client.Connection getConnection();

    // Lifecycle callback for shutdown
    void close();
}
```

### Responsibilities

1. **Establish connection** at startup using configuration
2. **Keep connection open** for the lifetime of the Quarkus app
3. **Provide connection** to `NatsConnection` wrapper
4. **Close connection gracefully** on app shutdown

### Design Notes

- **Not injected directly by users**: Developers inject `NatsConnection`, not this provider
- **Registered by processor**: `QuarkusEasyNatsProcessor` creates this bean
- **Singleton scope**: Only one instance per app (one connection per app)
- **Connection init timing**: Must be initialized before any user beans that inject `NatsConnection`

---

## Configuration Schema

Configuration controls connection establishment. Defined in `NatsConfiguration` class (also registered by processor).

### Configurable Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `quarkus.easynats.servers` | String list | ✅ Yes | None | Comma-separated NATS servers (e.g., `nats://host1:4222,nats://host2:4222`) |
| `quarkus.easynats.username` | String | Conditional | None | NATS auth username; required if password provided |
| `quarkus.easynats.password` | String | Conditional | None | NATS auth password; required if username provided |
| `quarkus.easynats.ssl-enabled` | Boolean | ❌ No | `false` | Enable SSL/TLS for connection |

### Validation Rules

1. **Servers required**: Must be provided; no default
2. **Credentials consistency**: If username provided, password must also be provided (and vice versa)
3. **Server format**: Each server must be valid NATS URL (e.g., `nats://host:4222`)
4. **SSL optional**: Only used if explicitly enabled

### Error Handling

**At Startup**:
- Missing `servers` → Application startup fails with clear error
- Incomplete credentials (username without password) → Startup fails with clear error
- Invalid server URL format → Startup fails with clear error
- Connection failure → Startup fails with clear error

**At Injection Time**:
- Pre-initialization injection → Bean instantiation fails with clear error

---

## State Diagram

```
┌─────────────────┐
│  Quarkus Startup │
└────────┬────────┘
         │
         ▼
┌──────────────────────────┐
│  NatsConnectionProvider  │  ← Creates and configures jnats Connection
│  reads config            │
└────────┬─────────────────┘
         │ (connection established)
         ▼
┌────────────────────────┐
│  NatsConnection        │  ← Registered as @Singleton CDI bean
│  (wrapper created)     │
└────────┬───────────────┘
         │
         ▼
┌──────────────────────────┐
│  Ready for Injection     │  ← Developers can @Inject NatsConnection
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│  Quarkus Shutdown        │  ← Connection closed by provider
│  App termination         │    (NOT by wrapper's no-op close())
└──────────────────────────┘
```

---

## Internal Integration: Using NatsConnection in Extension Components

The extension's existing components (`NatsPublisher`, `NatsSubscriber`, `NatsMessageHandler`, etc.) will be refactored to use `NatsConnection` internally instead of maintaining separate references to the underlying connection.

### Architecture

```
┌─────────────────────────────────────────────────────┐
│            Quarkus Application                       │
├─────────────────────────────────────────────────────┤
│                                                       │
│  ┌────────────────┐  ┌────────────────┐             │
│  │ NatsPublisher  │  │ NatsSubscriber │  ...        │
│  │ (existing)     │  │ (existing)     │             │
│  └────────┬───────┘  └────────┬───────┘             │
│           │                   │                     │
│           │ Uses internally   │                     │
│           │ (no public API    │                     │
│           │  change)          │                     │
│           └─────────┬─────────┘                     │
│                     ▼                                │
│         ┌──────────────────────┐                    │
│         │  NatsConnection      │ ← Shared singleton│
│         │  (this feature)      │                    │
│         └──────────┬───────────┘                    │
│                    │                                │
│                    ▼                                │
│         ┌──────────────────────┐                    │
│         │ io.nats.client.     │                    │
│         │ Connection          │                    │
│         └──────────┬───────────┘                    │
│                    │                                │
│                    ▼                                │
│         ┌──────────────────────┐                    │
│         │   NATS Server        │                    │
│         └──────────────────────┘                    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### Benefits

1. **Single Source of Truth**: All extension components and developer code use the same `NatsConnection` singleton
2. **Unified Monitoring**: Feature 011 (health checks) and future observability features monitor all NATS operations through one connection
3. **Cleaner Architecture**: No duplicate connection management; `NatsConnectionProvider` is the only component that manages the underlying connection
4. **Future-Proof**: All new features (health checks, tracing, metrics) automatically apply to all extension operations

### Implementation Notes

- `NatsPublisher` will inject `NatsConnection` in its constructor
- `NatsSubscriber` will inject `NatsConnection` and use it to create subscriptions
- `NatsMessageHandler` will use the injected `NatsConnection` for all operations
- `NatsMessageDeserializer` and other utilities that need connection access will receive it via constructor injection
- No public API changes needed; existing developer code continues to work (decorators like `@NatsPublisher` remain the same)
- Internal refactoring is transparent to users

---

## Design Rationale

### Why NatsConnection is a Wrapper, Not the Real Connection

- **Safety**: Prevents accidental closure by providing no-op `close()`
- **Simplicity**: Developers think they have "a connection" they can close with try-with-resources, but it's safe
- **Future-proof**: Enables health checks (Feature 011) to monitor connection via listeners without managing lifecycle
- **Clear intent**: Name `NatsConnection` is explicit; developers know it's the thing they interact with
- **Extension integration**: Existing components (`NatsPublisher`, `NatsSubscriber`, etc.) naturally inject and use the same singleton, unifying architecture

### Why No-op close() is Better than UnsupportedOperationException

- **Standard idiom**: Try-with-resources expects `close()` to be callable; no-op is standard approach
- **No surprises**: Code that uses try-with-resources works without modification; no exceptions to catch
- **Production-safe**: No runtime exceptions in production code; silent safety instead of loud failures

### Why Delegation, Not Inheritance

- **Composition over inheritance**: Wrapper can intercept calls and add behavior if needed in future
- **Cleaner**: Doesn't inherit all jnats implementation details
- **Flexible**: Can wrap different connection types in future if needed
- **Extension integration**: Makes it easy for internal components to inject and use the wrapper without tight coupling
