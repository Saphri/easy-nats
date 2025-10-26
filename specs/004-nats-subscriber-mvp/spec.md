# Feature Specification: @NatsSubscriber Annotation (MVP)

**Feature Branch**: `004-nats-subscriber-mvp`
**Created**: 2025-10-26
**Status**: Draft
**Input**: "as a developer I want to get the @NatsSubscriber working. focus on mvp. implicit empheral consumer in this slice and basic string payload"

## Clarifications

### Session 2025-10-26

- Q: How should the extension handle multiple `@NatsSubscriber` annotations in a single CDI bean? → A: Allow multiple annotated methods in the same class, each creating a separate consumer.
- Q: At what level should this exception be logged? → A: Log the exception at `ERROR` level.
- Q: What should happen if the NATS connection is not available during application startup? → A: Fail Fast: The application startup fails immediately if it cannot connect to NATS.
- Q: What strategy should be used to ensure consumers are truly ephemeral and unique across multiple application instances? → A: Server-Assigned Name: Do not specify a consumer name. Let the NATS server assign a unique, random name for each consumer instance.

## 1. User Scenarios & Testing

### User Story 1 - Consume String Messages from a NATS Subject (Priority: P1)

A developer wants to create a message consumer by annotating a method with `@NatsSubscriber`. The consumer should handle simple string messages from a specific subject. This is an MVP, so it should be an ephemeral consumer (it only receives messages while the application is running).

**Why this priority**: This is the core of the subscriber feature, enabling the most basic message consumption pattern.

**Independent Test**: Can be fully tested by creating a minimal Quarkus app with the extension, creating a method annotated with `@NatsSubscriber("my-subject")`, publishing a string message to "my-subject", and verifying that the annotated method was invoked with the correct message payload.

**Acceptance Scenarios**:

1.  **Given** a Quarkus application with the EasyNATS extension, **When** a developer annotates a method with `@NatsSubscriber("my-subject")`, **Then** the application subscribes to the "my-subject" NATS subject on startup.
2.  **Given** an active subscription, **When** a string message is published to "my-subject", **Then** the annotated method is invoked exactly once.
3.  **Given** the annotated method is invoked, **When** the method executes, **Then** the first parameter of the method is the string payload of the NATS message.

### Edge Cases

-   What happens if the annotated method has an incorrect signature (e.g., no parameters, wrong parameter type)? → The application build should fail with a clear error message.
-   What happens if the subject in the annotation is empty or invalid? → The application build should fail with a clear error message.
-   What happens if the NATS connection is unavailable at startup? → The application startup fails with a clear error message indicating that the NATS connection could not be established.
-   What happens if the NATS connection is lost after startup? → The underlying JNats client will automatically handle reconnection attempts in the background. During the disconnection, message delivery will be paused and will resume once the connection is re-established. The MVP will rely on the default reconnection logic provided by the client library.
-   What happens if a message causes repeated failures (exception thrown on every redelivery)? → JNats will indefinitely redeliver the message. The extension logs each exception but does not implement retry limits or dead-letter queue behavior. Operators can configure JNats-level policies separately.

## 2. Requirements

### Functional Requirements

-   **FR-001**: The extension MUST provide a `@NatsSubscriber` annotation that can be applied to methods.
-   **FR-002**: The `@NatsSubscriber` annotation MUST accept a non-empty string value representing the NATS subject to subscribe to.
-   **FR-003**: The annotated method MUST have a signature that accepts a single `String` parameter.
-   **FR-004**: The extension MUST automatically create an ephemeral NATS subscription for each method annotated with `@NatsSubscriber`, allowing the NATS server to assign a unique consumer name.
-   **FR-005**: When a message is received on the subscribed subject, the extension MUST invoke the annotated method with the message payload as the argument.
-   **FR-006**: The extension MUST automatically acknowledge (ack) a message if the annotated method completes successfully.
-   **FR-007**: The extension MUST automatically negatively acknowledge (nak) a message if the annotated method throws an exception.
-   **FR-008**: The extension MUST support multiple methods annotated with `@NatsSubscriber` within the same class, creating a distinct consumer for each one.
-   **FR-009**: The extension MUST validate that all subscriptions are successfully created during application startup. If any subscription fails to initialize, the application startup MUST fail with a clear, actionable error message.

### Non-Functional Requirements

-   **NFR-001**: The extension MUST log subscription creation events at INFO level, including subject and method name.
-   **NFR-002**: The extension MUST log message processing failures at ERROR level, including subject, method name, and full exception stack trace.
-   **NFR-003**: The extension MUST NOT log individual message payloads (to protect sensitive data in production).

### Key Entities

-   **`@NatsSubscriber`**: A method-level annotation to declare a NATS message consumer.
    -   `value` (String): The NATS subject.

## 3. Success Criteria

### Measurable Outcomes

-   **SC-001**: An integration test successfully demonstrates that a message published to a NATS subject is received and processed by a method annotated with `@NatsSubscriber`.
-   **SC-002**: The build fails with a `DefinitionException` if a method annotated with `@NatsSubscriber` does not have exactly one parameter of type `String`.
-   **SC-003**: The build fails with a `DefinitionException` if the subject provided to `@NatsSubscriber` is null or empty.
-   **SC-004**: Code coverage for the new subscriber logic is >= 80%.

## 4. Constraints & Assumptions

-   **Scope**: This MVP is strictly limited to `String` payloads and ephemeral consumers.
-   **Durable Consumers**: Durable consumers and consumer groups are out of scope for this MVP.
-   **Payload Types**: Support for other payload types (e.g., JSON, byte arrays) is out of scope for this MVP.
-   **Error Handling**: If the annotated method throws an exception, the message will be negatively acknowledged (nak'd), making it eligible for redelivery. The exception will be logged by the extension at the `ERROR` level.
-   **Concurrency**: The extension does not impose concurrency constraints. Concurrency behavior is determined by JNats's push-based subscription model (typically allowing parallel message delivery). Developers must ensure their annotated methods are thread-safe. JNats concurrency can be configured independently if needed.
-   **Shutdown**: The extension does not explicitly manage subscription lifecycle on application shutdown. Subscriptions are terminated when the underlying NATS connection closes as part of normal JNats shutdown. In-flight message processing is not waited on; nak'd messages are eligible for redelivery.