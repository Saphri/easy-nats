# Research: Quarkus Dev Services for NATS

**Date**: 2025-11-01
**Feature**: Dev Services Processor for NATS

This document outlines the research conducted to inform the implementation of the NATS Dev Services processor.

## 1. Quarkus Dev Services Lifecycle and Integration

### Research Task
Investigate the core mechanics of Quarkus Dev Services, including the build steps, configuration handling, and container lifecycle management.

### Findings
- **Entry Point**: Dev Services are implemented in a build-time processor within the `deployment` module.
- **Trigger**: They are triggered automatically in "dev" or "test" mode when a required service (like a database, message broker, etc.) is not explicitly configured.
- **Core Class**: `io.quarkus.deployment.builditem.DevServicesResultBuildItem` is the key build item. A processor produces this item to provide the configuration of the started service (e.g., the NATS server URL).
- **Container Management**: Quarkus has built-in support for Testcontainers. It manages a global container registry to enable sharing of containers between applications, which aligns with **FR-007**.
- **Lifecycle**: The processor must:
    1. Check if Dev Services is enabled (`quarkus.easynats.devservices.enabled`).
    2. Check if the NATS server URL (`quarkus.easynats.servers`) is already configured. If so, do nothing.
    3. Start the Testcontainer instance.
    4. Wait for the container to be ready.
    5. Produce a `DevServicesResultBuildItem` with the container's connection details.
    6. Quarkus handles the container's shutdown automatically when the application stops.

### Decision
The `NatsDevServicesProcessor` will be a standard Quarkus build-time processor. It will use a `@BuildStep` to check for existing configuration and, if needed, start a shared `NatsContainer` using the Testcontainers library. The processor will then produce a `DevServicesResultBuildItem` containing the dynamically generated `quarkus.easynats.servers` property.

## 2. Internal NatsContainer Usage

### Research Task
Confirm the capabilities of the existing internal `NatsContainer` and how it integrates with Quarkus Dev Services.

### Findings
- **Location**: The custom `NatsContainer` is located at `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/NatsContainer.java`.
- **Functionality**: It extends `GenericContainer` and is configured to start NATS in JetStream mode (`-js`), expose ports 4222 and 8222, and wait for the `/healthz` endpoint on port 8222. It provides `getNatsUrl()` to retrieve the connection string.
- **Integration**: This custom container can be directly used with Quarkus Dev Services, leveraging Testcontainers' `GenericContainer` capabilities. The existing `NatsContainer` already provides the necessary methods for starting, configuring, and retrieving the NATS URL.
- **Sharing**: To meet the requirement for a shared container (**FR-007**), the processor will use a `QuarkusLock` and a static `Closeable` resource to ensure only one container is started globally, as is standard for Quarkus Dev Services.

### Decision
The implementation will directly utilize the existing `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/NatsContainer.java`. The `NatsDevServicesProcessor` will instantiate this custom container with the `nats:latest` image and use the standard Quarkus locking and resource management pattern to ensure the container is a shared singleton.