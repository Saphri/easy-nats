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
# The NATS server URL
nats.servers=nats://localhost:4222
nats.username=admin
nats.password=secret
```

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

First, define your domain object. For native compilation, you'll need to add the `@RegisterForReflection` annotation.

```java
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
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

### CloudEvents

To publish a message with CloudEvents headers, use the `publishCloudEvent` method.

```java
// ... (inside a service)
public void sendCloudEvent(MyEvent event) {
    try {
        // The ce-type and ce-source are optional and will be auto-generated if null
        publisher.publishCloudEvent(event, "my.event.type", "/my-service");
        System.out.println("CloudEvent published: " + event.message);
    } catch (Exception e) {
        System.err.println("Failed to publish CloudEvent: " + e.getMessage());
    }
}
```

The extension will automatically add the required CloudEvents headers to the message, such as `ce-id`, `ce-time`, and `ce-specversion`.

## Building from Source

To build the extension from source, clone the repository and run the following command:

```bash
./mvnw clean install
```

## Running Integration Tests

To run the integration tests, use the `it` profile:

```bash
./mvnw clean install -Pit
```

This will start a NATS server in a Docker container and run the tests against it.