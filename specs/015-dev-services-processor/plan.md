# Implementation Plan: Dev Services Processor for NATS

**Branch**: `015-dev-services-processor` | **Date**: 2025-11-01 | **Spec**: [link](./spec.md)
**Input**: Feature specification from `/specs/015-dev-services-processor/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

This plan outlines the implementation of a Quarkus Dev Services processor for NATS. The processor will automatically start a shared NATS container during development and testing, injecting its configuration into the application. This aligns with the core Quarkus developer experience by removing the need for manual NATS server setup. The implementation will reside in the `deployment` module and leverage the internal `NatsContainer` class.
https://raw.githubusercontent.com/Saphri/quarkus-reactive-messaging-nats-jetstream/refs/heads/main/deployment/src/main/java/io/quarkiverse/reactive/messaging/nats/jetstream/deployment/JetStreamContainer.java is a working example of using NATS with testcontainers. Follow it closely.

## Technical Context

**Language/Version**: Java 21 (as per constitution)
**Primary Dependencies**: Quarkus Build-Time Processor API (`quarkus-arc`, `quarkus-core-deployment`), Internal `NatsContainer` (`org.mjelle.quarkus.easynats.deployment.devservices.NatsContainer`)
**Storage**: N/A (The feature manages a transient container, no persistent storage)
**Testing**: JUnit 5, Testcontainers, Awaitility
**Target Platform**: Quarkus 3.27.0+ applications running in Dev or Test mode
**Project Type**: Quarkus Extension (multi-module Maven project)
**Performance Goals**: The NATS container should start and be ready for connection within 10 seconds to avoid slowing down the development feedback loop.
**Constraints**: Must integrate with the existing Quarkus Dev Services lifecycle. Must not introduce runtime dependencies.
**Scale/Scope**: The scope is limited to starting/stopping a NATS container and injecting its configuration. It does not include stream/consumer provisioning.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Compilation Gate**: `./mvnw clean install -DskipTests` succeeds - **PENDING**
- **Unit Test Gate**: `./mvnw clean test` all pass (runtime + deployment) - **PENDING**
- **Integration Test Gate**: `./mvnw clean install -Pit` all pass (if feature touched NATS behavior) - **PENDING**
- **Code Coverage Gate**: New code ≥80% coverage (measured by Surefire/JaCoCo) - **PENDING**
- **Architecture Gate**: Verify no runtime module dependencies added without justification - **PASS** (All new dependencies are in the `deployment` module).
- **Native Image Gate** (future): GraalVM native image compilation succeeds - **PENDING**

## Project Structure

### Documentation (this feature)

```text
specs/015-dev-services-processor/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
deployment/
└── src/
    └── main/
        └── java/
            └── io/quarkus/easynats/deployment/
                └── devservices/
                    └── NatsContainer.java          # Custom NATS Testcontainer
                └── NatsDevServicesProcessor.java   # Core logic for starting/stopping the container
integration-tests/
└── src/
    └── test/
        └── java/
            └── io/quarkus/easynats/it/
                └── NatsDevServicesTest.java        # Test to verify dev services functionality
```

**Structure Decision**: The implementation aligns with the existing Quarkus extension multi-module structure. The custom `NatsContainer` will be used, and the core logic will be in a new `NatsDevServicesProcessor` class within the `deployment` module. A new integration test will be added to the `integration-tests` module to validate the end-to-end functionality.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A       | N/A        | N/A                                 |