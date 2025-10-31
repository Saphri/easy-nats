# Feature Specification: Health Probe Stability

**Feature Branch**: `013-health-probe-stability`  
**Created**: 2025-10-31  
**Status**: Draft  
**Input**: User description: "Health startup probe should not report status changes after ready status have been confirmed"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Startup Probe Final State (Priority: P1)

As a system operator, I want the startup probe to report a conclusive "UP" status once the application is fully initialized, so that the orchestrator (e.g., Kubernetes) can confidently transition to using the liveness probe without premature restarts.

**Why this priority**: This is the core requirement of the feature. It ensures application stability in a containerized environment.

**Independent Test**: The startup probe's status can be checked independently. Once it reports "UP", subsequent checks should consistently report "UP".

**Acceptance Scenarios**:

1. **Given** the application is starting up, **When** the startup probe is checked, **Then** it may report "DOWN".
2. **Given** the application has fully initialized, **When** the startup probe is checked, **Then** it reports "UP".
3. **Given** the startup probe has reported "UP" once, **When** it is checked again, **Then** it must continue to report "UP".

### Edge Cases

- What happens if a downstream dependency becomes unavailable after the application has started? The startup probe, once "UP", should remain "UP". Liveness or readiness probes should handle this scenario.
- How does the system handle a very slow startup? The startup probe should continue to report "DOWN" until startup is complete, without flapping.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The startup health probe MUST report "DOWN" until the application is fully initialized and ready to accept traffic.
- **FR-002**: The startup health probe MUST report "UP" once the `NatsConnectionListener` reports a `CONNECTED` event.
- **FR-003**: Once the startup health probe has reported "UP", it MUST NOT change its status back to "DOWN" for any reason.
- **FR-004**: The determination of "fully initialized" MUST be based on the state provided by the `ConnectionStatusHolder`.
- **FR-005**: The existing startup probe, `NatsStartupCheck`, MUST be modified to implement the latching behavior.

## Clarifications

### Session 2025-10-31

- Q: What is the precise trigger for the startup probe to switch to "UP"? → A: When NATS via the @runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/health/NatsConnectionListener.java reports the event CONNECTED
- Q: Should the NatsConnectionHealthCheck directly observe the NatsConnectionListener events, or should it query a ConnectionStatusHolder? → A: Query a ConnectionStatusHolder. The existing NatsStartupCheck.java should be modified.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a successful startup sequence, the startup probe transitions from "DOWN" to "UP" exactly once.
- **SC-002**: After the first "UP" status, the startup probe reports "UP" for 100% of subsequent checks.
- **SC-003**: The application is not prematurely terminated by the container orchestrator due to a flapping startup probe.