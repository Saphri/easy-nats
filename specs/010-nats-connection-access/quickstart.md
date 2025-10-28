# Quickstart: Using NatsConnection for Advanced Operations

**Feature**: 010-nats-connection-access | **Date**: 2025-10-28

## Overview

This quickstart shows developers how to inject and use `NatsConnection` for advanced NATS operations not covered by the extension's publisher/subscriber annotations.

## Prerequisites

- Quarkus 3.27.0+ with EasyNATS extension enabled
- NATS JetStream server running and configured
- Configuration properties set (`quarkus.easynats.servers`, etc.)

## Configuration

Add to `application.properties`:

```properties
quarkus.easynats.servers=nats://localhost:4222
quarkus.easynats.username=user
quarkus.easynats.password=pass
quarkus.easynats.ssl-enabled=false
```

Or `application.yaml`:

```yaml
quarkus:
  easynats:
    servers: nats://localhost:4222
    username: user
    password: pass
    ssl-enabled: false
```

## Basic Usage: Inject and Publish

```java
import org.mjelle.quarkus.easynats.NatsConnection;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/orders")
public class OrderResource {

    @Inject
    NatsConnection natsConnection;

    @POST
    public void submitOrder(Order order) throws Exception {
        byte[] payload = jackson.writeValueAsBytes(order);
        natsConnection.publish("orders.new", payload);
    }
}
```

## Advanced: JetStream Access

Access JetStream for durable consumers and message acknowledgment:

```java
@Inject
NatsConnection natsConnection;

public void publishWithAck(Order order) throws Exception {
    JetStreamContext js = natsConnection.createJetStreamContext();
    byte[] payload = jackson.writeValueAsBytes(order);
    PublishAck ack = js.publish("orders", payload);
    System.out.println("Message sequence: " + ack.getSequence());
}
```

## Advanced: Metadata Access

Query server information:

```java
@Inject
NatsConnection natsConnection;

public void printServerInfo() {
    ServerInfo info = natsConnection.getServerInfo();
    System.out.println("Server: " + info.getHost());
    System.out.println("Version: " + info.getVersion());
    System.out.println("Cluster: " + info.getCluster());
}
```

## Advanced: Key-Value Store

Use NATS key-value buckets for distributed storage:

```java
@Inject
NatsConnection natsConnection;

public void cacheUserData(String userId, User user) throws Exception {
    KeyValue kv = natsConnection.keyValue("users");
    kv.put(userId, jackson.writeValueAsBytes(user));
}

public User getCachedUser(String userId) throws Exception {
    KeyValue kv = natsConnection.keyValue("users");
    KeyValueEntry entry = kv.get(userId);
    return jackson.readValue(entry.getValue(), User.class);
}
```

## Advanced: Key-Value Management

Create and manage KV buckets:

```java
@Inject
NatsConnection natsConnection;

public void setupBuckets() throws Exception {
    KeyValueManagement kvm = natsConnection.keyValueManagement();

    // Create a new bucket
    kvm.create("users");

    // List all buckets
    List<String> buckets = kvm.getBucketNames();
    System.out.println("Available buckets: " + buckets);

    // Delete a bucket
    kvm.delete("old-bucket");
}
```

## Safe: Try-with-Resources

Use try-with-resources for scoped connection access (the `close()` is a safe no-op):

```java
@Inject
NatsConnection natsConnection;

public void sendMessage(String subject, byte[] data) {
    try (NatsConnection conn = natsConnection) {
        conn.publish(subject, data);
        // When this block exits, close() is called
        // But the underlying connection remains open
        // (no-op close design prevents accidental shutdown)
    } catch (Exception e) {
        System.err.println("Failed to send message: " + e.getMessage());
    }
}
```

## Safe: Listener Registration (for Health Checks)

Register a listener to monitor connection state (used by health check feature):

```java
@Inject
NatsConnection natsConnection;

@PostConstruct
public void registerListener() {
    natsConnection.setConnectionListener((event) -> {
        switch (event.getType()) {
            case CONNECTED:
                System.out.println("Connected to NATS");
                break;
            case DISCONNECTED:
                System.out.println("Disconnected from NATS");
                break;
            case RECONNECTED:
                System.out.println("Reconnected to NATS");
                break;
            case ASYNC_ERROR:
                System.err.println("Async error: " + event.getException());
                break;
        }
    });
}
```

## Full Example: Order Service

```java
import org.mjelle.quarkus.easynats.NatsConnection;
import io.nats.client.api.JetStreamContext;
import io.nats.client.api.PublishAck;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

@Path("/orders")
@ApplicationScoped
public class OrderService {

    @Inject
    NatsConnection natsConnection;

    @POST
    @Path("/{id}/submit")
    public void submitOrder(@PathParam String id, Order order) throws Exception {
        // Publish to JetStream for durability
        JetStreamContext js = natsConnection.createJetStreamContext();
        byte[] payload = jacksonObjectMapper.writeValueAsBytes(order);
        PublishAck ack = js.publish("orders." + order.getType(), payload);
        System.out.println("Order " + id + " published at sequence " + ack.getSequence());
    }

    @GET
    @Path("/info")
    public ServerInfo getServerInfo() {
        return natsConnection.getServerInfo();
    }

    @POST
    @Path("/cache/{userId}")
    public void cacheUser(@PathParam String userId, User user) throws Exception {
        KeyValue kv = natsConnection.keyValue("users");
        kv.put(userId, jacksonObjectMapper.writeValueAsBytes(user));
    }

    @GET
    @Path("/cache/{userId}")
    public User getCachedUser(@PathParam String userId) throws Exception {
        KeyValue kv = natsConnection.keyValue("users");
        KeyValueEntry entry = kv.get(userId);
        if (entry == null) {
            throw new WebApplicationException("User not found", 404);
        }
        return jacksonObjectMapper.readValue(entry.getValue(), User.class);
    }
}
```

## Important Notes

### Thread Safety

`NatsConnection` is thread-safe for concurrent use by multiple threads. All methods delegate to the thread-safe underlying jnats connection:

```java
// Safe: multiple threads can use the same instance
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 10; i++) {
    executor.submit(() -> {
        natsConnection.publish("topic", "message".getBytes());
    });
}
```

### Closing the Connection

**DO NOT** close `NatsConnection`. The `close()` method is a safe no-op:

```java
// WRONG: Calling close() does nothing (no-op)
natsConnection.close();  // No-op; connection remains open

// CORRECT: Use try-with-resources (close is still a no-op, but idiomatic)
try (NatsConnection conn = natsConnection) {
    conn.publish("topic", data);
}

// CORRECT: Just use it directly (injection manages lifecycle)
natsConnection.publish("topic", data);
```

### Configuration Errors

Configuration errors cause application startup to fail. Ensure all required properties are set:

```properties
# Required - no default
quarkus.easynats.servers=nats://localhost:4222

# If username is provided, password MUST also be provided
quarkus.easynats.username=user
quarkus.easynats.password=pass

# Optional - defaults to false
quarkus.easynats.ssl-enabled=false
```

### Reconnection Handling

The underlying jnats client handles automatic reconnection with exponential backoff. `NatsConnection` does not intervene:

```java
// NatsConnection does not handle reconnection
// jnats handles it automatically:
// - Network goes down
// - jnats detects disconnection
// - jnats automatically attempts to reconnect
// - When reconnected, your code continues as normal
try {
    natsConnection.publish("topic", data);
} catch (NatsException e) {
    // If publish fails, it's a real error (not a transient disconnect)
    System.err.println("Publish failed: " + e.getMessage());
}
```

## Testing

Use Quarkus test annotations to inject `NatsConnection` in tests:

```java
@QuarkusTest
class OrderServiceTest {

    @Inject
    NatsConnection natsConnection;

    @Test
    void testPublish() throws Exception {
        natsConnection.publish("test", "data".getBytes());
        // Assertion based on test setup
    }
}
```

## Next Steps

- For health checks based on connection state, see Feature 011 (SmallRye Health integration)
- For more details on JetStream API, see [NATS JetStream documentation](https://docs.nats.io/nats-concepts/jetstream)
- For CloudEvents support, see Feature 005 documentation
