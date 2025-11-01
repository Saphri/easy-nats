# Quickstart: Using NATS Dev Services

**Date**: 2025-11-01
**Feature**: Dev Services Processor for NATS

This guide explains how to use the automatic NATS Dev Services in your Quarkus application.

## Prerequisites

- A Quarkus project.
- Docker installed and running.

## Usage

1.  **Add the Dependency**: Add the `quarkus-easy-nats` extension to your project's `pom.xml`:

    ```xml
    <dependency>
        <groupId>io.quarkus.easynats</groupId>
        <artifactId>quarkus-easy-nats</artifactId>
        <version>${project.version}</version>
    </dependency>
    ```

2.  **Run in Dev Mode**: Start your application in development mode:

    ```bash
    ./mvnw quarkus:dev
    ```

3.  **Automatic NATS Server**: If you have not configured a `quarkus.easynats.servers` property in your `application.properties`, the extension will automatically:
    - Start a NATS server in a Docker container.
    - Configure your application to connect to this server.

    You will see logs indicating that the NATS Dev Service has started.

4.  **Develop and Test**: Your application is now connected to a NATS server, and you can develop and test your messaging logic without any manual setup.

## Disabling Dev Services

To disable this feature, add the following to your `application.properties`:

```properties
quarkus.easynats.devservices.enabled=false
```