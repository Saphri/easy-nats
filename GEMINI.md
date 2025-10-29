# Project Overview

This project is a Quarkus extension for EasyNATS, a JetStream NATS messaging library. The extension provides seamless integration with Quarkus, including support for CloudEvents.

The project is structured as a multi-module Maven project:
- `deployment`: Contains the build-time processor for the Quarkus extension. This processor discovers `@NatsSubscriber` annotations, registers beans, and configures reflection for native image compilation.
- `runtime`: Contains the core runtime logic for the extension, including `NatsConnectionManager` for managing the NATS connection, a generic `NatsPublisher` for sending messages, and the `@NatsSubscriber` annotation.
- `integration-tests`: Contains integration tests for the extension.

# Features

- **`NatsConnectionManager`**: Manages the NATS connection lifecycle, automatically connecting on startup and disconnecting on shutdown.
- **`NatsPublisher`**: A generic publisher that can be injected to send messages to NATS subjects. It supports typed payloads and automatically wraps them in the CloudEvents 1.0 format.
- **`@NatsSubscriber`**: An annotation that allows methods to subscribe to a NATS subject. The extension automatically discovers these methods and registers them as message listeners.
- **`@NatsSubject`**: A qualifier annotation used to inject a `NatsPublisher` with a default subject.
- **CloudEvents**: The `NatsPublisher` automatically wraps payloads in the CloudEvents 1.0 format, providing metadata such as `specversion`, `type`, `source`, `id`, `time`, and `datacontenttype`.
- **Native Image**: The extension is designed to work with Quarkus' native image compilation, with the build processor handling the necessary reflection registrations.

# Building and Running

The project can be built using Maven. The following commands can be used to build and test the project:

```bash
# Build the project and run the tests
./mvnw clean install

# Run the integration tests
./mvnw clean install -Pit
```

# Development Conventions

The project follows the standard conventions for Quarkus extensions. The code is written in Java and uses Maven for dependency management and building.

The project uses the standard Quarkus testing framework for integration tests. The tests are located in the `integration-tests` module.

## Active Technologies
- Java 21 + Quarkus 3.27.0
- NATS JetStream client (jnats 2.23.0)
- Jackson Databind
- Java 21 + Quarkus 3.27.0, NATS JetStream client, SmallRye Health (011-nats-health-endpoints)

## Recent Changes
- 011-nats-health-endpoints: Added Java 21 + Quarkus 3.27.0, NATS JetStream client, SmallRye Health
