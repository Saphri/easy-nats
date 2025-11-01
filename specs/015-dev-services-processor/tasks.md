# Actionable Tasks: Dev Services Processor for NATS

**Branch**: `015-dev-services-processor` | **Date**: 2025-11-01 | **Spec**: [spec.md](./spec.md)

This document outlines the implementation tasks for the "Dev Services Processor for NATS" feature.

## Phase 1: Setup

- [X] T001 Add Testcontainers dependency (`org.testcontainers:testcontainers`) to the `deployment` module's `pom.xml`.

## Phase 2: User Story 1 - Automatic NATS Server in Dev Mode

**Goal**: As a Quarkus application developer, I want the NATS server to start automatically when I run my application in development or test mode, so that I can develop and test my application without manually configuring a NATS server.

**Independent Test**: This can be tested by running a Quarkus application with the extension in dev mode and verifying that a NATS server is started and the application can connect to it.

### Implementation Tasks

- [X] T002 [US1] Create the `NatsDevServicesProcessor.java` file in `deployment/src/main/java/io/quarkus/easynats/deployment/`.
- [X] T003 [US1] Implement a `@BuildStep` in `NatsDevServicesProcessor.java` that checks if Dev Services should be activated (i.e., not disabled via config and no `quarkus.easynats.servers` property is set).
- [X] T004 [US1] Implement the logic in `NatsDevServicesProcessor.java` to start the internal `NatsContainer` using the standard Quarkus Dev Services pattern (with `QuarkusLock` for sharing).
- [X] T005 [US1] In `NatsDevServicesProcessor.java`, produce a `DevServicesResultBuildItem` with the NATS server URL from the running container.

### Test Tasks

- [X] T006 [P] [US1] Create the integration test file `NatsDevServicesTest.java` in `integration-tests/src/test/java/io/quarkus/easynats/it/`.
- [X] T007 [US1] Write a test in `NatsDevServicesTest.java` to verify that when the application starts in test mode without a NATS URL configured, it can successfully connect to a NATS server.
- [X] T008 [P] [US1] Write a test in `NatsDevServicesTest.java` to verify that if `quarkus.easynats.servers` is configured, the Dev Service does not start a container.
- [X] T009 [P] [US1] Write a test in `NatsDevServicesTest.java` to verify that if `quarkus.easynats.devservices.enabled` is set to `false`, the Dev Service does not start a container.

## Phase 3: Polish & Cross-Cutting Concerns

- [X] T010 Add logging to `NatsDevServicesProcessor.java` to indicate when the NATS container is starting and what the connection URL is.
- [X] T011 Ensure that if the container fails to start (e.g., Docker is not running), the build fails with a clear and informative error message.

## Dependencies

- **User Story 1** is the only user story and has no dependencies.

## Parallel Execution

The following tasks can be worked on in parallel:

- **Within User Story 1**:
  - `T006`, `T008`, and `T009` (test creation) can be started in parallel with `T002` (implementation file creation). The tests will fail until the implementation tasks are complete, following the TDD principle.

## Implementation Strategy

The implementation will focus on delivering User Story 1 as the Minimum Viable Product (MVP). The tasks are ordered to follow a Test-Driven Development (TDD) approach where possible, with test stubs created first, followed by the implementation that makes them pass.
