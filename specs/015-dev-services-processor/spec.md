# Feature Specification: Dev Services Processor for NATS

**Feature Branch**: `015-dev-services-processor`
**Created**: 2025-11-01
**Status**: Draft
**Input**: User description: "**Goal:** Implement the Dev Services processor that uses the custom `NatsContainer` to automatically start a NATS server. **Context:** This step connects the `NatsContainer` class created previous to the Quarkus build process. The focus is solely on launching the container and injecting its configuration into the application."

## Clarifications

### Session 2025-11-01
- Q: What are the security considerations for the NATS container in dev mode? → A: Security is not a primary concern for dev mode; focus on functionality and ease of use.
- Q: Which version of the NATS server should be used in the container? → A: Use the latest stable version of the NATS server.
- Q: What should be the configuration property names for the NATS server URL and for disabling Dev Services? → A: `quarkus.easynats.servers` for the NATS server URL and `quarkus.easynats.devservices.enabled` for disabling Dev Services.

### Session 2025-11-01
- Q: How should the Dev Service handle multiple Quarkus applications running simultaneously? → A: A single, shared NATS container is started for all applications.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic NATS Server in Dev Mode (Priority: P1)

As a Quarkus application developer, I want the NATS server to start automatically when I run my application in development or test mode, so that I can develop and test my application without manually configuring a NATS server.

**Why this priority**: This is the core functionality of the Dev Services processor and is essential for a smooth developer experience.

**Independent Test**: This can be tested by running a Quarkus application with the extension in dev mode and verifying that a NATS server is started and the application can connect to it.

**Acceptance Scenarios**:

1. **Given** a Quarkus application with the `quarkus-easy-nats` extension, **When** the application is started in dev mode (`./mvnw quarkus:dev`), **Then** a NATS server is started automatically in a container.
2. **Given** a NATS server is running via Dev Services, **When** the application attempts to connect to NATS, **Then** it connects successfully using the automatically configured server URL.
3. **Given** the application is running in dev mode, **When** the application is shut down, **Then** the NATS container is also shut down.

---

### Edge Cases

- What happens if the user has a NATS server already running and configured? The Dev Service should not start a new one.
- How does the system handle errors during container startup (e.g., Docker not running)? The build should fail with a clear error message.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST detect when a Quarkus application is running in "dev" or "test" mode.
- **FR-002**: The system MUST automatically start a NATS server in a container if no NATS server URL is explicitly configured by the user.
- **FR-003**: The system MUST inject the container's NATS server URL into the application's configuration properties using the property name `quarkus.easynats.servers`.
- **FR-004**: The system MUST shut down the NATS container when the Quarkus application stops.
- **FR-005**: The system MUST provide a way for users to disable the automatic startup of the NATS server using the property name `quarkus.easynats.devservices.enabled`.
- **FR-006**: The NATS server used in the Dev Services container MUST be the latest stable version.
- **FR-007**: The Dev Services MUST ensure that only a single NATS container is started and shared across multiple co-located Quarkus applications running simultaneously.

### Non-Functional Requirements

- **NFR-001**: Security for the Dev Services container is not a primary concern; the focus is on ease of use and rapid development cycles. The container may run with relaxed security settings (e.g., no authentication).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When a Quarkus application with the extension is run in dev mode without a `nats.url` configured, a NATS container is started 100% of the time.
- **SC-002**: The application successfully connects to the Dev Services NATS server within 5 seconds of starting.
- **SC-003**: When the Quarkus application is shut down, the associated NATS container is stopped within 5 seconds.
- **SC-004**: If `quarkus.easynats.devservices.enabled=false` is set, no NATS container is started.
- **SC-005**: When multiple Quarkus applications with the extension are run in dev mode on the same machine, only one NATS container is started and shared among them.