# Quickstart: Using NatsPublisher in Your Quarkus App

**Status**: MVP 001
**Updated**: 2025-10-26

## Overview

This quickstart demonstrates how to use the EasyNATS extension to publish string messages to NATS in a Quarkus application.

---

## Prerequisites

- Java 21+
- Quarkus 3.27.0+
- NATS broker running (local or remote)
  - For development: `docker run -p 4222:4222 nats:latest -js`
  - Or use docker-compose (see project root)

---

## Step 1: Create a Quarkus Application

```bash
quarkus create app org.example:nats-demo
cd nats-demo
```

---

## Step 2: Add the EasyNATS Extension

Update `pom.xml` to include the EasyNATS runtime module:

```xml
<dependency>
    <groupId>io.quarkus.easynats</groupId>
    <artifactId>quarkus-easynats</artifactId>
    <version>${project.version}</version>
</dependency>
```

Or add via CLI:

```bash
quarkus ext add io.quarkus.easynats:quarkus-easynats
```

---

## Step 3: Inject NatsPublisher

Create a REST endpoint or service that uses `NatsPublisher`:

```java
package org.example;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import io.quarkus.easynats.NatsPublisher;

@Path("/publish")
public class PublisherResource {
    
    @Inject
    NatsPublisher publisher;
    
    @GET
    public Response publish(@QueryParam("message") String message) {
        try {
            publisher.publish(message != null ? message : "hello");
            return Response.ok("Message published").build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("Failed to publish: " + e.getMessage())
                .build();
        }
    }
}
```

---

## Step 4: Configure NATS Connection

Update `src/main/resources/application.properties`:

```properties
# NATS connection (MVP 001: hardcoded defaults)
# For local development with default docker-compose:
# No configuration needed; defaults to nats://localhost:4222 (guest/guest)

# Future MVP 002+ will support configuration:
# nats.servers=nats://localhost:4222
# nats.username=guest
# nats.password=guest
```

**Note**: MVP 001 has hardcoded NATS broker URL and credentials. Configuration support is deferred to MVP 002+.

---

## Step 5: Set Up NATS Broker & JetStream

Before running your application, create the JetStream stream and subject:

```bash
# Start NATS with JetStream
docker run -p 4222:4222 nats:latest -js

# Create stream and subject (in separate terminal)
nats stream add test_stream --subjects test --discard old --max-age=-1 --replicas=1
```

Verify stream was created:

```bash
nats stream list
# Output: test_stream
```

---

## Step 6: Run Your App

```bash
quarkus dev
```

Your app will start and connect to NATS automatically. If NATS is unreachable, startup will fail with a clear error message.

---

## Step 7: Test Publishing

In a new terminal, publish a message:

```bash
curl "http://localhost:8080/publish?message=Hello%20NATS"
# Response: Message published
```

Verify the message arrived in NATS:

```bash
nats sub test
# Waiting for messages on "test"...
# [10:23:45] subj: test / tries: 1 / body: Hello NATS
```

---

## Complete Example Code

### REST Endpoint

```java
package org.example;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.easynats.NatsPublisher;

@Path("/nats")
@Produces(MediaType.APPLICATION_JSON)
public class NatsResource {

    private final NatsPublisher publisher;

    // Constructor injection (REQUIRED)
    public NatsResource(NatsPublisher publisher) {
        this.publisher = publisher;
    }

    @GET
    @Path("/publish/{message}")
    public PublishResult publish(String message) throws Exception {
        publisher.publish(message);
        return new PublishResult(
            "success",
            "Message published to NATS subject 'test'",
            message
        );
    }

    public static class PublishResult {
        public String status;
        public String message;
        public String payload;

        public PublishResult(String status, String message, String payload) {
            this.status = status;
            this.message = message;
            this.payload = payload;
        }
    }
}
```

### Service Layer

```java
package org.example;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import io.quarkus.easynats.NatsPublisher;

@ApplicationScoped
public class EventPublisher {

    private static final Logger log = Logger.getLogger(EventPublisher.class);
    private final NatsPublisher natsPublisher;

    // Constructor injection (REQUIRED)
    public EventPublisher(NatsPublisher natsPublisher) {
        this.natsPublisher = natsPublisher;
    }

    public void publishEvent(String eventType, String eventData) {
        try {
            String message = eventType + ": " + eventData;
            natsPublisher.publish(message);
            log.infof("Event published: %s", message);
        } catch (Exception e) {
            log.errorf(e, "Failed to publish event: %s", eventType);
            // Handle error: retry, alert, or propagate
        }
    }
}
```

### Integration Test

```java
package org.example;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import io.quarkus.easynats.NatsPublisher;
import io.nats.client.Connection;
import io.nats.client.Nats;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

@QuarkusTest
public class NatsPublisherTest {

    // @Inject ONLY allowed in tests
    @Inject
    NatsPublisher publisher;

    @Test
    public void testPublisherCanBeInjected() {
        assertThat(publisher).isNotNull();
    }

    @Test
    public void testPublisherPublishesMessage() throws Exception {
        publisher.publish("test message");
        // Test passes if no exception thrown
    }

    @Test
    public void testMessageAppearsOnBroker() throws Exception {
        // Connect to same broker
        Connection conn = Nats.connect("nats://localhost:4222");

        // Publish via extension
        publisher.publish("hello world");

        // Use Awaitility (NEVER Thread.sleep)
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                var jetStream = conn.jetStream();
                var sub = jetStream.subscribe("test");
                var msg = sub.nextMessage(Duration.ofMillis(500));

                String received = new String(msg.getData(), StandardCharsets.UTF_8);
                assertThat(received).isEqualTo("hello world");
                msg.ack();
            });

        conn.close();
    }
}
```

---

## Troubleshooting

### "Connection refused" on startup

**Cause**: NATS broker not running or not on expected port

**Solution**:
```bash
# Start NATS locally
docker run -p 4222:4222 nats:latest -js

# Or verify NATS is already running
nats server info
```

### "Stream not found" error when publishing

**Cause**: JetStream stream `test_stream` doesn't exist

**Solution**:
```bash
nats stream add test_stream --subjects test --discard old --max-age=-1 --replicas=1
```

### Messages not appearing on broker

**Cause**: Publisher throwing exception silently (application catching Exception)

**Solution**:
```java
try {
    publisher.publish(message);
} catch (Exception e) {
    e.printStackTrace();  // Log the error
    throw e;  // Re-throw or handle appropriately
}
```

---

## What's Next?

- **Async Publishing**: MVP 002 will add `publishAsync()` for non-blocking publish
- **Type Safety**: MVP 002 will add `NatsPublisher<T>` with compile-time type checking
- **Subscriber**: MVP 003 will add `@NatsSubscriber` for message consumption
- **Configuration**: MVP 002 will add properties-based NATS connection configuration
- **Observability**: MVP 003 will add tracing and health checks

---

## API Reference

### NatsPublisher.publish(String message)

- **Throws**: `Exception` (caller must handle)
- **Blocks**: Until NATS acknowledges (fire-and-forget model)
- **Subject**: Hardcoded to `test` (configuration deferred)
- **Encoding**: UTF-8 string to bytes

See `contracts/nats-publisher-api.md` for detailed API contract.

---

## Resources

- [NATS Documentation](https://docs.nats.io)
- [NATS JetStream Guide](https://docs.nats.io/nats-concepts/jetstream)
- [Quarkus Documentation](https://quarkus.io)
- [EasyNATS Feature Spec](./spec.md)
- [Implementation Plan](./plan.md)
