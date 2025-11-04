# Quarkus EasyNATS

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Quarkus extension for integrating with NATS JetStream, designed for simplicity and developer experience. This extension provides seamless integration with Quarkus, including support for typed payloads and CloudEvents.

**Note:** This project is currently under active development.

## Why Choose Quarkus EasyNATS Over Raw JNATS?

This extension eliminates the boilerplate and complexity of raw JNATS while providing a modern, production-ready messaging experience. Here are the top 10 reasons to use it:

### 1. 🚀 **Zero-Configuration Dev Services**
Stop manually managing NATS containers. The extension automatically discovers NATS running in Docker Compose (via labels) and configures connection settings—including credentials and TLS—without a single line of configuration. Perfect for local development and CI/CD pipelines.

**Raw JNATS**: Manual container setup, connection URLs, credential management
**Quarkus EasyNATS**: Automatic discovery and configuration

### 2. 🛡️ **Type-Safe Generic Publishers**
Publish strongly-typed messages with `NatsPublisher<OrderEvent>` for compile-time type safety. The extension validates your types on first publish and provides clear error messages if Jackson can't serialize them.

**Raw JNATS**: Manual `byte[]` encoding, runtime serialization failures
**Quarkus EasyNATS**: Compile-time type safety with automatic JSON serialization

```java
@Inject @NatsSubject("orders") NatsPublisher<OrderEvent> publisher;
publisher.publish(new OrderEvent("ORDER-123", 99.99)); // Type-safe!
```

### 3. 📬 **Declarative Subscribers with @NatsSubscriber**
No more manual consumer creation, message loops, or lifecycle management. Just annotate a method with `@NatsSubscriber` and the extension handles ephemeral/durable consumer setup, message deserialization, and graceful shutdown.

**Raw JNATS**: 50+ lines of boilerplate per subscriber
**Quarkus EasyNATS**: 3 lines with `@NatsSubscriber`

```java
@NatsSubscriber(stream = "orders", consumer = "order-processor")
public void onOrder(OrderEvent event) {
    // Message automatically deserialized and acknowledged
}
```

### 4. ☁️ **Automatic CloudEvents 1.0 Support**
Every message is automatically wrapped as a CloudEvents 1.0 binary-mode event with spec-compliant headers (`ce-type`, `ce-source`, `ce-id`, `ce-time`, etc.). No manual header management or spec compliance checking—it just works.

**Raw JNATS**: Manual CloudEvents header generation and validation
**Quarkus EasyNATS**: Transparent CloudEvents wrapping with zero code

### 5. 💉 **Native Quarkus CDI Integration**
Inject `NatsPublisher`, `NatsConnection`, and custom configuration beans anywhere in your application. The extension manages the NATS connection lifecycle (startup, shutdown, reconnection) automatically as a singleton.

**Raw JNATS**: Manual connection management, singleton patterns, shutdown hooks
**Quarkus EasyNATS**: CDI-managed lifecycle with constructor injection

### 6. ✅ **Flexible Acknowledgment Modes**
Choose between automatic acknowledgment (simple) or explicit control (advanced). Use `NatsMessage<T>` to manually `ack()`, `nak()`, `nakWithDelay()`, or `term()` messages for sophisticated error handling and retry logic.

**Raw JNATS**: Manual ack tracking across methods
**Quarkus EasyNATS**: Choose auto-ack or explicit control per subscriber

```java
@NatsSubscriber(stream = "orders", consumer = "processor")
public void onOrder(NatsMessage<OrderEvent> msg) {
    try {
        process(msg.payload());
        msg.ack();
    } catch (Exception e) {
        msg.nakWithDelay(Duration.ofSeconds(30)); // Retry with backoff
    }
}
```

### 7. 🏥 **Production-Ready Health Checks**
Three-tier health probing out-of-the-box: **Startup** (latch on first connection), **Readiness** (tolerates reconnects), and **Liveness** (prevents premature termination). Kubernetes-ready with no configuration.

**Raw JNATS**: Manual health check implementation
**Quarkus EasyNATS**: `/q/health/live`, `/q/health/ready`, `/q/health/started` work immediately

### 8. 🔍 **Distributed Tracing with OpenTelemetry**
Automatic W3C Trace Context propagation via `traceparent` and `tracestate` headers. Every publish and subscribe operation creates OpenTelemetry spans for end-to-end observability across services.

**Raw JNATS**: Manual trace context injection and extraction
**Quarkus EasyNATS**: Automatic tracing with Quarkus OpenTelemetry extension

### 9. 🧪 **Build-Time Type Validation**
Validates message types at compile-time (subscribers) and first-publish (publishers). Rejects primitives, arrays, and types without no-arg constructors with helpful error messages guiding you to solutions.

**Raw JNATS**: Runtime serialization surprises
**Quarkus EasyNATS**: Early validation with actionable error messages

### 10. ⚡ **GraalVM Native Image Support**
Compiles to native executables with subsecond startup and minimal memory footprint. The extension automatically registers reflection metadata for your subscriber types—no manual configuration needed.

**Raw JNATS**: Manual reflection configuration, trial-and-error native builds
**Quarkus EasyNATS**: Native-ready out-of-the-box with automatic reflection registration

---

## Additional Features

*   **Custom NATS Options via CDI:** Advanced users can provide custom `io.nats.client.Options` beans for fine-grained control (connection timeouts, SSL/TLS, retry policies)
*   **Docker-Compose Discovery:** Detects NATS containers by label (`nats-jetstream-server`) and extracts credentials from environment variables
*   **Clear Error Messages:** All exceptions include actionable guidance with configurable payload logging
*   **Durable Consumer Support:** Works with pre-configured JetStream consumers or creates ephemeral consumers on-demand
*   **JetStream Support:** Built on top of NATS JetStream for reliable, persistent messaging

## Getting Started

### Prerequisites

*   Java 21+
*   Maven 3.8+
*   An existing Quarkus project
*   A running NATS server with JetStream enabled.

### 1. Add the Dependency

Add the following dependency to your project's `pom.xml`:

```xml
<dependency>
    <groupId>org.mjelle.quarkus.easynats</groupId>
    <artifactId>quarkus-easy-nats</artifactId>
    <version>999-SNAPSHOT</version>
</dependency>
```

### 2. Configure the NATS Connection

Add the following configuration to your `application.properties` file:

```properties
# Required: NATS server URL(s)
quarkus.easynats.servers=nats://localhost:4222

# Optional: Authentication
quarkus.easynats.username=admin
quarkus.easynats.password=secret

# Optional: TLS/SSL configuration (for tls:// URLs)
# quarkus.easynats.tls-configuration-name=nats-tls
# quarkus.tls.nats-tls.trust-store.pem.certs=certificates/ca.crt
```

📖 For complete configuration options including TLS/SSL setup, multiple servers, and production examples, see the **[Configuration Guide](docs/CONFIGURATION.md)**.

## Usage

### Advanced Usage: Explicit Acknowledgment and Metadata

For advanced use cases, such as manual acknowledgment or accessing message headers, you can use the `NatsMessage<T>` wrapper. This gives you full control over the message lifecycle.

```java
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;

@ApplicationScoped
public class MyAdvancedConsumer {

    @NatsSubscriber(stream = "my-events", consumer = "my-consumer")
    public void onMessage(NatsMessage<MyEvent> message) {
        String traceId = message.headers().getFirst("traceparent");
        System.out.println("Trace ID: " + traceId);

        // Manually acknowledge the message
        message.ack();
    }
}
```

### Basic Untyped Publisher

For simple string messages, you can inject a `NatsPublisher` and specify the subject with the `@NatsSubject` annotation.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

@ApplicationScoped
public class MyNatsProducer {

    @Inject
    @NatsSubject("my-subject")
    NatsPublisher publisher;

    public void sendMessage(String message) {
        try {
            publisher.publish(message);
            System.out.println("Message published: " + message);
        } catch (Exception e) {
            System.err.println("Failed to publish message: " + e.getMessage());
        }
    }
}
```

### Typed Publisher

You can also publish any Java object as a JSON payload by using a typed `NatsPublisher<T>`.

First, define your domain object.

```java
public class MyEvent {
    public String message;

    public MyEvent() {}

    public MyEvent(String message) {
        this.message = message;
    }
}
```

Then, inject a typed `NatsPublisher` and use it to publish your object.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

@ApplicationScoped
public class MyTypedProducer {

    @Inject
    @NatsSubject("my-events")
    NatsPublisher<MyEvent> publisher;

    public void sendEvent(MyEvent event) {
        try {
            publisher.publish(event);
            System.out.println("Event published: " + event.message);
        } catch (Exception e) {
            System.err.println("Failed to publish event: " + e.getMessage());
        }
    }
}
```

### Explicit Acknowledgment

For advanced error handling, you can take full control over message acknowledgment. To do this, change your subscriber method to accept a `NatsMessage<T>` parameter. This gives you access to `ack()` and `nak()` methods.

**Note**: The framework automatically naks messages that fail to deserialize or violate CloudEvent requirements, so your subscriber method is only invoked for successfully deserialized messages.

```java
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;
import java.time.Duration;

@ApplicationScoped
public class MyNatsConsumer {

    @NatsSubscriber(stream = "my-events", consumer = "my-consumer")
    public void onMessage(NatsMessage<MyEvent> message) {
        MyEvent event = message.payload();
        try {
            // Process the event
            process(event);

            // Manually acknowledge the message
            message.ack();
            System.out.println("Message processed and acknowledged.");

        } catch (Exception e) {
            // Reject the message and request redelivery after 10 seconds
            message.nakWithDelay(Duration.ofSeconds(10));
            System.err.println("Failed to process message, requesting redelivery.");
        }
    }

    private void process(MyEvent event) throws Exception {
        // Your business logic here
    }
}
```

## Building from Source

To build the extension from source, clone the repository and run the following command:

```bash
./mvnw clean install
```

## Documentation

📖 **[Full Documentation Index](docs/INDEX.md)** - Start here for complete documentation

### Quick Links

**Getting Started**
- [Quick Start Guide](docs/QUICKSTART.md) - 5-minute introduction
- [Configuration Guide](docs/CONFIGURATION.md) - Connection, authentication, TLS/SSL setup

**Type System**
- [Jackson Compatibility Guide](docs/JACKSON_COMPATIBILITY_GUIDE.md) - Supported types & best practices
- [Wrapper Pattern Guide](docs/WRAPPER_PATTERN.md) - Wrapping primitives and arrays
- [Jackson Annotations Guide](docs/JACKSON_ANNOTATIONS_GUIDE.md) - Customization with annotations

**Observability**
- [Distributed Tracing Guide](docs/DISTRIBUTED_TRACING.md) - W3C Trace Context propagation and OpenTelemetry integration

**Troubleshooting**
- [Error Troubleshooting Guide](docs/ERROR_TROUBLESHOOTING.md) - Common errors & solutions

**Architecture**
- [Feature Specification](specs/007-typed-serialization/spec.md) - Requirements & design
- [Implementation Tasks](specs/007-typed-serialization/tasks.md) - Status & metrics
- [Distributed Tracing Specification](specs/012-distributed-tracing-spans/spec.md) - Tracing feature requirements

**Key Concepts**
- Publish and subscribe to strongly-typed Java objects
- Automatic JSON serialization/deserialization via Jackson
- CloudEvents support for event metadata
- Type validation at build-time (subscribers) and runtime (publishers)
- Clear error messages guide you to solutions

## Running Integration Tests

To run the integration tests, use the `it` profile:

```bash
./mvnw clean install -Pit
```

This will start a NATS server in a Docker container and run the tests against it.