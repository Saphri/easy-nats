# API Contract: NatsConnection Interface

**Feature**: 010-nats-connection-access | **Date**: 2025-10-28 | **Module**: runtime

## Overview

`NatsConnection` is a CDI-injectable interface that exposes the NATS client's `io.nats.client.Connection` API with safety guarantees. It implements `AutoCloseable` for try-with-resources support.

## Public API

### Package

```
org.mjelle.quarkus.easynats.NatsConnection
```

### Class Declaration

```java
public class NatsConnection implements AutoCloseable {
    // ... methods below
}
```

## Method Contracts

### Publishing Operations

#### `void publish(String subject, byte[] data)`

**Contract**:
- Publishes a message to the specified subject
- Delegates to underlying `io.nats.client.Connection.publish(subject, data)`
- **Throws**: `io.nats.client.NatsException` if publish fails
- **Thread-safe**: Yes (delegated to jnats)
- **Example**:
  ```java
  natsConnection.publish("orders", jsonBytes);
  ```

#### `void publish(String subject, Headers headers, byte[] data)`

**Contract**:
- Publishes a message with NATS headers (e.g., CloudEvents headers)
- Delegates to underlying `io.nats.client.Connection.publish(subject, headers, data)`
- **Throws**: `io.nats.client.NatsException` if publish fails
- **Thread-safe**: Yes
- **Example**:
  ```java
  Headers headers = new Headers().add("ce-type", "order.created");
  natsConnection.publish("orders", headers, jsonBytes);
  ```

### Subscription Operations

#### `Subscription subscribe(String subject)`

**Contract**:
- Creates a synchronous subscription to the subject
- Delegates to underlying `io.nats.client.Connection.subscribe(subject)`
- **Returns**: `io.nats.client.Subscription` object
- **Throws**: `io.nats.client.NatsException` if subscribe fails
- **Thread-safe**: Yes
- **Example**:
  ```java
  Subscription sub = natsConnection.subscribe("orders");
  Message msg = sub.nextMessage(Duration.ofSeconds(5));
  ```

#### `Dispatcher createDispatcher(MessageHandler handler)`

**Contract**:
- Creates an asynchronous message dispatcher
- Delegates to underlying `io.nats.client.Connection.createDispatcher(handler)`
- **Returns**: `io.nats.client.Dispatcher` for async message processing
- **Throws**: `io.nats.client.NatsException` if dispatcher creation fails
- **Thread-safe**: Yes
- **Example**:
  ```java
  Dispatcher dispatcher = natsConnection.createDispatcher((msg) -> {
      System.out.println("Received: " + new String(msg.getData()));
  });
  dispatcher.subscribe("orders");
  ```

### JetStream Operations

#### `JetStreamContext createJetStreamContext()`

**Contract**:
- Creates a JetStream context for accessing JetStream APIs
- Delegates to underlying `io.nats.client.Connection.createJetStreamContext()`
- **Returns**: `io.nats.client.api.JetStreamContext`
- **Throws**: `io.nats.client.NatsException` if JetStream creation fails
- **Thread-safe**: Yes
- **Purpose**: Access durable streams, persistent consumers, message acknowledgment
- **Example**:
  ```java
  JetStreamContext js = natsConnection.createJetStreamContext();
  PublishAck ack = js.publish("orders", message);
  ```

#### `JetStreamContext createJetStreamContext(JetStreamOptions options)`

**Contract**:
- Creates a JetStream context with custom options
- Delegates to underlying `io.nats.client.Connection.createJetStreamContext(options)`
- **Returns**: `io.nats.client.api.JetStreamContext`
- **Throws**: `io.nats.client.NatsException` if JetStream creation fails
- **Thread-safe**: Yes
- **Example**:
  ```java
  JetStreamOptions opts = JetStreamOptions.builder()
      .domain("my-domain")
      .build();
  JetStreamContext js = natsConnection.createJetStreamContext(opts);
  ```

### Connection Metadata

#### `ServerInfo getServerInfo()`

**Contract**:
- Returns information about the connected NATS server
- Delegates to underlying `io.nats.client.Connection.getServerInfo()`
- **Returns**: `io.nats.client.ServerInfo` with server version, host, cluster info, etc.
- **Throws**: `io.nats.client.NatsException` if info retrieval fails
- **Thread-safe**: Yes
- **Example**:
  ```java
  ServerInfo info = natsConnection.getServerInfo();
  System.out.println("Server version: " + info.getVersion());
  ```

### Listener Registration (for Health Checks / Feature 011)

#### `void setConnectionListener(ConnectionListener listener)`

**Contract**:
- Registers a listener for connection state changes
- Delegates to underlying `io.nats.client.Connection.setConnectionListener(listener)`
- **Purpose**: Enable Feature 011 (health checks) to monitor connection state
- **Throws**: `io.nats.client.NatsException` if listener registration fails
- **Thread-safe**: Yes
- **Callback events**: Connected, disconnected, reconnected, async error, slow consumer, etc.
- **Example**:
  ```java
  natsConnection.setConnectionListener((event) -> {
      System.out.println("Connection event: " + event.getType());
  });
  ```

### Key-Value Store Access (Advanced Use Cases)

#### `KeyValue keyValue(String bucketName)`

**Contract**:
- Accesses a key-value store bucket
- Delegates to underlying `io.nats.client.Connection.keyValue(bucketName)`
- **Returns**: `io.nats.client.api.KeyValue` interface
- **Throws**: `io.nats.client.NatsException` if KV creation fails
- **Thread-safe**: Yes
- **Purpose**: Advanced use case; access distributed KV store built on JetStream
- **Example**:
  ```java
  KeyValue kv = natsConnection.keyValue("my-bucket");
  kv.put("key", "value");
  String value = new String(kv.get("key").getValue());
  ```

#### `KeyValueManagement keyValueManagement()`

**Contract**:
- Accesses key-value management operations (create, delete, list buckets)
- Delegates to underlying `io.nats.client.Connection.keyValueManagement()`
- **Returns**: `io.nats.client.api.KeyValueManagement` interface
- **Throws**: `io.nats.client.NatsException` if management creation fails
- **Thread-safe**: Yes
- **Purpose**: Advanced use case; manage KV bucket lifecycle
- **Example**:
  ```java
  KeyValueManagement kvm = natsConnection.keyValueManagement();
  kvm.create("my-bucket");
  List<String> buckets = kvm.getBucketNames();
  ```

### AutoCloseable Implementation

#### `void close()`

**Contract**:
- **Does NOT close the underlying connection**
- **Is a safe no-op** that returns immediately
- **Throws**: Never (IOException not actually thrown, despite interface signature)
- **Purpose**: Enable try-with-resources usage safely
- **Thread-safe**: Yes (thread-safe no-op)
- **Example**:
  ```java
  try (NatsConnection conn = natsConnection) {
      // Use connection...
      // When try block exits, close() is called (no-op)
      // Connection remains open for other threads/beans
  }
  ```

**Why no-op?**: The underlying connection is a shared singleton. Closing it would break all other subscribers and publishers. By implementing `close()` as a no-op, developers can safely use try-with-resources patterns without unintended side effects.

---

## Full Method Delegation List

| Category | Method | Notes |
|----------|--------|-------|
| **Publishing** | `publish(subject, data)` | Delegates |
| | `publish(subject, headers, data)` | Delegates |
| | All other publish variants | Delegate to jnats |
| **Subscription** | `subscribe(subject)` | Delegates |
| | `subscribe(subject, queue)` | Delegates |
| | All subscription variants | Delegate to jnats |
| **Dispatcher** | `createDispatcher(handler)` | Delegates |
| | All dispatcher methods | Delegate to jnats |
| **JetStream** | `createJetStreamContext()` | Delegates |
| | `createJetStreamContext(options)` | Delegates |
| **Metadata** | `getServerInfo()` | Delegates |
| | `getConnectedUrl()` | Delegates |
| | `getStatus()` | Delegates |
| **Listeners** | `setConnectionListener(listener)` | Delegates |
| | `getConnectionListener()` | Delegates |
| **KeyValue** | `keyValue(bucket)` | Delegates |
| | `keyValueManagement()` | Delegates |
| **Lifecycle** | `close()` | **No-op** (safe) |
| | `isClosed()` | Delegates (connection not closed by wrapper) |

---

## Design Principles

### Transparency

`NatsConnection` is designed to be **transparent**. Developers should be able to use it exactly as they would use `io.nats.client.Connection`, with the addition of CDI injection and safe try-with-resources support.

### Delegation-First

All method calls are delegated directly to the underlying connection. No interception, no modification, no overhead beyond the method call itself.

### Fail-Fast on Close

While `close()` is a no-op, future implementations could log a warning or metric if desired. For now, it's completely silent to avoid surprising developers with warnings.

### Future-Proof

The interface exposes all current jnats connection methods. If jnats adds new methods, `NatsConnection` should be updated to delegate them as well.

---

## CDI Usage Examples

### Constructor Injection

```java
@ApplicationScoped
public class OrderProcessor {
    private final NatsConnection natsConnection;

    public OrderProcessor(NatsConnection natsConnection) {
        this.natsConnection = natsConnection;
    }

    public void processOrder(Order order) throws Exception {
        JetStreamContext js = natsConnection.createJetStreamContext();
        js.publish("orders", jackson.writeValueAsBytes(order));
    }
}
```

### Try-with-Resources

```java
public void sendMessage(String subject, byte[] data) {
    try (NatsConnection conn = natsConnection) {
        conn.publish(subject, data);
    } // close() is called but does nothing (safe)
}
```

### Advanced: Listener Registration

```java
public OrderHealthCheck {
    private final NatsConnection natsConnection;

    public OrderHealthCheck(NatsConnection natsConnection) {
        this.natsConnection = natsConnection;
        // Register listener for health monitoring (Feature 011)
        natsConnection.setConnectionListener((event) -> {
            if (event.getType() == ConnectionEvent.DISCONNECTED) {
                // Notify health check system
            }
        });
    }
}
```
