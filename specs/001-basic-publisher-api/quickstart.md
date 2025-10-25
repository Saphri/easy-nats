# Quickstart: Basic NatsPublisher (MVP)

**Feature**: Basic NatsPublisher API
**Date**: 2025-10-25

## Overview

This quickstart demonstrates how to use the Quarkus EasyNATS extension to publish simple string messages to NATS JetStream in the MVP version.

---

## Prerequisites

1. **Java 21** or later
2. **Quarkus 3.27.0** or later
3. **NATS broker** with JetStream enabled
   - **Option A (Recommended for development)**: Use Docker Compose
     ```bash
     # From project root, start NATS + observability stack
     docker-compose -f integration-tests/docker-compose-devservices.yml up -d

     # NATS will be available at: nats://localhost:4222
     # Credentials: username=guest, password=guest

     # Stop with:
     docker-compose -f integration-tests/docker-compose-devservices.yml down
     ```
   - **Option B (Manual Docker)**: Single container
     ```bash
     docker run -d -p 4222:4222 nats:latest -js
     ```
   - **Option C (Binary)**: Download and run [nats-server](https://nats.io/download/)

4. **NATS CLI** (optional but recommended for stream setup)
   - Install: https://github.com/nats-io/natscli/releases
   - Used to create and manage streams

---

## Step 0: Initialize NATS Stream

After starting the NATS broker, create the JetStream stream for the "test" subject:

```bash
# Using NATS CLI (recommended)
nats stream add test_stream \
  --subjects test \
  --discard old \
  --max-age=-1 \
  --replicas=1

# Verify stream creation
nats stream list
```

**What this does**:
- Creates a stream named `test_stream`
- Listens to subject `test` (the subject our publisher uses)
- Uses `discard old` policy (removes oldest messages when limit reached)
- No expiration (max-age=-1)
- Single replica (suitable for local dev)

**If stream already exists**, you'll see a prompt to update it. Accept the update or delete and recreate:
```bash
nats stream delete test_stream
# Then run the add command again
```

**Note**: For development, the stream will be created automatically if missing (future enhancement). For MVP, manual creation ensures predictable testing environment.

---

## Step 1: Create a Quarkus Application

```bash
quarkus create app --java=21 my-publisher-app
cd my-publisher-app
```

---

## Step 2: Add the EasyNATS Extension Dependency

Edit `pom.xml` and add the EasyNATS extension:

```xml
<dependency>
    <groupId>io.quarkus.easynats</groupId>
    <artifactId>quarkus-easy-nats</artifactId>
    <version>0.1.0</version> <!-- Match your extension version -->
</dependency>
```

---

## Step 3: Inject and Use NatsPublisher

Create a simple resource or service to use the publisher:

**File**: `src/main/java/org/acme/ExampleResource.java`

```java
package org.acme;

import io.quarkus.easynats.NatsPublisher;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/publish")
public class ExampleResource {

    @Inject
    NatsPublisher publisher;

    @GET
    public String publishMessage() throws Exception {
        publisher.publish("hello");
        return "Message published!";
    }
}
```

---

## Step 4: Run the Application

```bash
./mvnw quarkus:dev
```

The application starts and connects to the NATS broker.

---

## Step 5: Send a Message

```bash
curl http://localhost:8080/publish
```

**Response**:
```
Message published!
```

---

## Step 6: Verify Message Arrival (Optional)

Use `nats` CLI to monitor the subject:

```bash
# Terminal 1: Subscribe to the "test" subject
nats sub test

# Terminal 2: Call the endpoint
curl http://localhost:8080/publish

# Terminal 1 should show:
# Subscribing on [test]
# Received on subject "test": hello
```

---

## Code Example: Different Scenarios

### Scenario 1: RESTful Endpoint Publishing

```java
@Path("/api/messages")
public class MessageResource {

    @Inject
    NatsPublisher publisher;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response publish(String message) throws Exception {
        publisher.publish(message);
        return Response.ok("Published: " + message).build();
    }
}
```

**Usage**:
```bash
curl -X POST -H "Content-Type: text/plain" \
     -d "Hello, NATS!" \
     http://localhost:8080/api/messages
```

---

### Scenario 2: Scheduled Publishing

```java
@ApplicationScoped
public class ScheduledPublisher {

    @Inject
    NatsPublisher publisher;

    @Scheduled(every = "10s")
    void publishHeartbeat() throws Exception {
        publisher.publish("heartbeat: " + System.currentTimeMillis());
    }
}
```

---

### Scenario 3: Publishing on Event

```java
@ApplicationScoped
public class EventListener {

    @Inject
    NatsPublisher publisher;

    void onApplicationStart(@Observes StartupEvent event) throws Exception {
        publisher.publish("Application started");
    }
}
```

---

## Troubleshooting

### Error: "Unable to connect to NATS broker"

**Cause**: NATS broker is not running or not reachable on `localhost:4222`

**Solution**:
```bash
# Start NATS in Docker
docker run -d -p 4222:4222 nats:latest -js
```

---

### Error: "NullPointerException when calling publisher.publish()"

**Cause**: Publisher was not injected (CDI container did not recognize the `@Inject` annotation)

**Solution**: Ensure you have:
1. `quarkus-easy-nats` dependency in `pom.xml`
2. JetStream enabled in NATS broker (default for `nats:latest` Docker image)

---

### Error: "JetStreamApiException: Stream not found"

**Cause**: NATS broker does not have a stream configured for the "test" subject

**Solution**: The MVP assumes the broker auto-creates streams. If not enabled, configure manually:

```bash
nats stream add --subjects test --name test_stream
```

---

## What's Next?

This MVP demonstrates the core publisher pattern. Future versions will add:

- **Type-safe publishers**: `NatsPublisher<MyEvent>` with serialization
- **Subject configuration**: `@NatsSubject("custom-subject")` annotation
- **Subscribers**: `@NatsSubscriber` annotation for message handlers
- **CloudEvents support**: Automatic CloudEvents wrapping
- **Health checks**: `/q/health` endpoint for readiness probes
- **Observability**: Distributed tracing via W3C Trace Context headers

---

## Reference

- **Full Spec**: See `spec.md` in this directory
- **Data Model**: See `data-model.md`
- **API Contract**: See `contracts/publisher-api.md`
- **NATS Documentation**: https://docs.nats.io/
- **JetStream Guide**: https://docs.nats.io/nats-concepts/jetstream
- **Quarkus Guide**: https://quarkus.io/guides/
