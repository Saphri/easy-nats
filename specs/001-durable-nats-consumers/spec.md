# Feature Specification: Durable Consumers for @NatsSubscriber

**Feature Branch**: `001-durable-nats-consumers`
**Created**: 2025-10-27
**Status**: Draft
**Input**: User description: "as a developer I would like to use preconfigured durable consumers with my @NatsSubscriber"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure and use a durable consumer (Priority: P1)

As a developer, I want to configure a durable consumer in my application properties and use it with a `@NatsSubscriber` so that my message consumer can survive restarts without losing messages.

**Why this priority**: This is the core functionality of the feature and provides the primary value to the user.

**Independent Test**: A simple Quarkus application with a single `@NatsSubscriber` method can be created. The test will involve sending a message, stopping the application, restarting it, and verifying that the message is processed.

**Acceptance Scenarios**:

1. **Given** a durable consumer is configured in `application.properties`, **When** a method is annotated with `@NatsSubscriber` referencing that consumer, **Then** the application starts without errors and the subscriber is active.
2. **Given** a message is published to the subject the durable consumer is listening on while the application is running, **When** the message is received, **Then** it is processed successfully.
3. **Given** a message is published to the subject while the application is stopped, **When** the application is started, **Then** the message is received and processed successfully.

### Edge Cases

- What happens if the durable consumer name in `@NatsSubscriber` does not exist in the configuration? The application should fail to start with a clear error message.
- How does the system handle NATS connection errors? The subscriber should attempt to reconnect according to the configured policy.

## Assumptions

- A NATS server is running.
- A NATS JetStream stream has been defined.
- A durable consumer has been preconfigured on the stream.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow developers to define one or more durable consumer configurations in `application.properties`.
- **FR-002**: The `@NatsSubscriber` annotation MUST include a property to specify the name of the durable consumer configuration to use.
- **FR-003**: If a durable consumer is specified in `@NatsSubscriber` but not defined in the configuration, the application MUST fail to start.
- **FR-004**: The extension MUST ensure the durable consumer exists on the NATS JetStream server on application startup.
- **FR-005**: The subscriber MUST automatically acknowledge messages after they have been processed successfully by the annotated method.
- **FR-006**: If the processing of a message fails (i.e., the annotated method throws an exception), the message MUST NOT be acknowledged, allowing for redelivery.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can implement a durable message subscriber by adding 3-5 lines of configuration in `application.properties` and one annotation property.
- **SC-002**: In end-to-end tests, a 100% message processing guarantee is maintained across application restarts.
- **SC-003**: The configuration model for durable consumers is clearly documented and easy to understand, resulting in minimal configuration-related support questions.