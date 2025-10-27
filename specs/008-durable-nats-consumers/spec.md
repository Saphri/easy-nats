# Feature Specification: Durable Consumers for @NatsSubscriber

**Feature Branch**: `008-durable-nats-consumers`
**Continues**: `007-typed-serialization`
**Created**: 2025-10-27
**Status**: Draft
**Input**: User description: "as a developer I would like to use preconfigured durable consumers with my @NatsSubscriber"

## Clarifications

### Session 2025-10-27

- Q: What should be the default values for the `stream` and `consumer` annotation properties? → A: No defaults; both are required parameters when using durable consumer mode.
- Q: When using a durable consumer, what validation applies to the `subject` property? → A: Build fails if `subject` is non-empty when `stream` and `consumer` are provided.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Use a pre-configured durable consumer (Priority: P1)

As a developer, I want to specify a stream and a durable consumer name in my `@NatsSubscriber` annotation so that my message consumer can bind to a pre-configured durable consumer on the NATS server and survive restarts without losing messages.

**Why this priority**: This is the core functionality of the feature and provides the primary value to the user.

**Independent Test**: A simple Quarkus application with a single `@NatsSubscriber` method can be created. The test will involve sending a message, stopping the application, restarting it, and verifying that the message is processed by the durable consumer.

**Acceptance Scenarios**:

1. **Given** a durable consumer is pre-configured on a NATS stream, **When** a method is annotated with `@NatsSubscriber` specifying the stream and consumer name, **Then** the application starts without errors and the subscriber is active.
2. **Given** a message is published to the subject the durable consumer is listening on while the application is running, **When** the message is received, **Then** it is processed successfully.
3. **Given** a message is published to the subject while the application is stopped, **When** the application is started, **Then** the message is received and processed successfully.

### Edge Cases

- What happens if the durable consumer specified in `@NatsSubscriber` does not exist on the NATS server? The application should fail to start with a clear error message.

## Assumptions

- A NATS server is running.
- A NATS JetStream stream has been defined.
- A durable consumer has been preconfigured on the stream. The extension is not responsible for creating or configuring the consumer.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `@NatsSubscriber` annotation MUST include a `subject` property to specify the NATS subject to subscribe to, with no default value (required when using ephemeral consumer mode).
- **FR-002**: The `@NatsSubscriber` annotation MUST include a `stream` property with no default value (required when using durable consumer mode).
- **FR-003**: The `@NatsSubscriber` annotation MUST include a `consumer` property with no default value (required when using durable consumer mode).
- **FR-004**: The annotation MUST enforce that either `subject` is provided (ephemeral consumer mode), or both `stream` and `consumer` are provided (durable consumer mode), but not both sets.
- **FR-005**: The annotation MUST validate at build time that if `stream` and `consumer` are provided, the `subject` property MUST be empty; build fails if `subject` is non-empty in durable consumer mode.
- **FR-006**: If a `consumer` is specified in `@NatsSubscriber`, the application MUST verify that the consumer exists on the specified `stream` on the NATS server at startup.
- **FR-007**: If the specified durable consumer does not exist, the application MUST fail to start with a clear error message.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can bind to a durable consumer by adding `stream` and `consumer` properties to the `@NatsSubscriber` annotation.
- **SC-002**: A developer can subscribe to a subject using an ephemeral consumer by adding a `subject` property to the `@NatsSubscriber` annotation.
- **SC-003**: In end-to-end tests, a 100% message processing guarantee is maintained across application restarts when using a durable consumer.
- **SC-004**: The usage of `subject`, `stream`, and `consumer` properties, and their validation rules, are clearly documented.