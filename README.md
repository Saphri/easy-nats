# Quarkus EasyNATS Extension

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Quarkus extension for integrating with [EasyNATS](https://github.com/mjelle/easy-nats), a JetStream NATS messaging library. This extension provides seamless integration with Quarkus, including support for CloudEvents.

**Note:** This project is currently under active development.

## Features

*   **Simplified NATS Publishing:** Inject a `NatsPublisher` bean to easily publish messages to NATS subjects.
*   **Quarkus Integration:** Automatically configures and manages the NATS connection lifecycle.
*   **JetStream Support:** Built on top of NATS JetStream for reliable messaging.

## Getting Started

### Prerequisites

*   Java 21+
*   Maven 3.8+
*   An existing Quarkus project

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
quarkus.easynats.url=nats://localhost:4222
```

### 3. Use the NatsPublisher

Inject the `NatsPublisher` bean into your application and use it to publish messages:

```java
import jakarta.enterprise.context.ApplicationScoped;
import org.mjelle.quarkus.easynats.NatsPublisher;

@ApplicationScoped
public class MyNatsProducer {

    private final NatsPublisher publisher;

    public MyNatsProducer(NatsPublisher publisher) {
        this.publisher = publisher;
    }

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
