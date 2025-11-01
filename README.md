# Quarkus EasyNATS

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Quarkus extension for integrating with NATS JetStream, designed for simplicity and developer experience. This extension provides seamless integration with Quarkus, including support for typed payloads and CloudEvents.

**Note:** This project is currently under active development.

## Features

*   **Simplified NATS Publishing:** Inject a `NatsPublisher` bean to easily publish messages to NATS subjects.
*   **Typed Payloads:** Publish any Java object as a JSON payload with `NatsPublisher<T>`.
*   **Annotation-Driven:** Use the `@NatsSubject` annotation to configure the NATS subject declaratively.
*   **CloudEvents Support:** First-class support for the CloudEvents specification with automatic header generation.
*   **Quarkus Integration:** Automatically configures and manages the NATS connection lifecycle.
*   **JetStream Support:** Built on top of NATS JetStream for reliable messaging.
*   **Explicit Ack/Nak Control:** Manually acknowledge or reject messages for advanced error handling.
*   **Distributed Tracing:** Automatic W3C Trace Context propagation with OpenTelemetry integration for end-to-end observability.

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

For advanced error handling, you can take full control over message acknowledgment. To do this, change your subscriber method to accept a `NatsMessage<T>` parameter. This disables automatic acknowledgment and gives you access to `ack()` and `nak()` methods.

```java
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.annotation.NatsSubscriber;
import java.time.Duration;

@ApplicationScoped
public class MyNatsConsumer {

    @NatsSubscriber(subject = "my-events", consumer = "my-consumer")
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