# Tasks: Health Probe Stability

**Feature**: [Health Probe Stability](https/github.com/mjell/quarkus-easy-nats/issues/13)

This document outlines the tasks required to implement the Health Probe Stability feature.

## Phase 1: Foundational Tasks

- [X] T001 Review existing `NatsStartupCheck` implementation in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/health/NatsStartupCheck.java`

## Phase 2: User Story 1 - Startup Probe Final State

**Goal**: As a system operator, I want the startup probe to report a conclusive "UP" status once the application is fully initialized, so that the orchestrator (e.g., Kubernetes) can confidently transition to using the liveness probe without premature restarts.

**Independent Test Criteria**:
1. The startup probe endpoint (`/q/health/started`) returns "DOWN" before the NATS connection is established.
2. After the NATS connection is established, the endpoint returns "UP".
3. If the NATS connection is lost after the initial connection, the endpoint continues to return "UP".

### Implementation Tasks

- [X] T002 [US1] Add an `AtomicBoolean` state field to `NatsStartupCheck.java` to track the latched "UP" status.
- [X] T003 [US1] Modify the `call()` method in `NatsStartupCheck.java` to implement the latching logic. If the state field is true, return "UP" immediately. Otherwise, check the connection status from `ConnectionStatusHolder`, and if connected, set the state field to true before returning "UP".

### Testing Tasks

- [X] T004 [P] [US1] Create a new integration test file `HealthProbeStabilityTest.java` in `integration-tests/src/test/java/io/jefrajames/easynats/it/`.
- [X] T005 [US1] Add a test to `HealthProbeStabilityTest.java` to verify the startup probe reports "DOWN" initially.
- [X] T006 [US1] Add a test to `HealthProbeStabilityTest.java` to verify the startup probe reports "UP" after the NATS connection is established.
- [X] T007 [US1] Add a test to `HealthProbeStabilityTest.java` to verify the startup probe continues to report "UP" even if the NATS connection is subsequently lost.

## Phase 3: Polish & Cross-Cutting Concerns

- [X] T008 Review and update any relevant documentation in the `docs/` directory to reflect the new startup probe behavior.
- [X] T009 Run the full build and integration tests (`./mvnw clean install -Pit`) to ensure no regressions have been introduced.

## Dependencies

- **User Story 1** is the only user story and has no dependencies on other stories.

## Parallel Execution Examples

- **T002** (implementation) and **T004** (test setup) can be started in parallel.
- **T005**, **T006**, and **T007** (individual test cases) can be developed in parallel once the initial test class setup is complete.

## Implementation Strategy

The implementation will focus on modifying the existing `NatsStartupCheck` class to introduce a stateful latch. An integration test will be created to validate the behavior in a running Quarkus application, simulating the connection lifecycle events. This approach ensures the feature is delivered as a single, testable unit.
