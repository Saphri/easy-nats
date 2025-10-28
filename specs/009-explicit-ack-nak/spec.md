# Feature Specification: Explicit Ack/Nak Control

**Feature Branch**: `009-explicit-ack-nak`
**Continues**: `008-durable-nats-consumers`
**Created**: 2025-10-28
**Status**: Draft
**Input**: User description: "As a developer I would like to have explicit ack/nak control in some scenarios"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Acknowledge a message after processing completes (Priority: P1)

As a developer, I want to explicitly acknowledge a message after my subscriber method completes processing so that the message is marked as delivered and the NATS server does not redeliver it.

**Why this priority**: This is the core functionality for reliable message processing. Developers need explicit control over when a message is considered successfully processed, especially for long-running operations or when error handling is complex.

**Independent Test**: A Quarkus application with a `@NatsSubscriber` method can call an acknowledgment API after processing completes. The test verifies the message is marked as delivered and is not redelivered.

**Acceptance Scenarios**:

1. **Given** a subscriber method that processes a message, **When** the method explicitly acknowledges the message after completion, **Then** the message is marked as delivered on the NATS server.
2. **Given** a subscriber method that acknowledges a message, **When** the subscriber restarts, **Then** the message is not redelivered.
3. **Given** a subscriber method that calls acknowledge, **When** the message is already acknowledged, **Then** the second acknowledge call is silently ignored (idempotent).

---

### User Story 2 - Reject a message with negative acknowledgment (Priority: P1)

As a developer, I want to explicitly reject (negative acknowledge) a message when processing fails so that the message is redelivered to the subscriber, enabling retry patterns.

**Why this priority**: Negative acknowledgment is essential for implementing robust error handling. When message processing fails, developers need a way to signal this to the broker so the message can be retried.

**Independent Test**: A Quarkus application with a `@NatsSubscriber` method can call a negative acknowledgment API when processing fails. The test verifies the message is redelivered after a configured delay.

**Acceptance Scenarios**:

1. **Given** a subscriber method that encounters an error during processing, **When** the method explicitly negative acknowledges the message, **Then** the message is marked as not delivered and is eligible for redelivery.
2. **Given** a message that is negative acknowledged, **When** the redelivery delay elapses, **Then** the message is redelivered to a subscriber.
3. **Given** a subscriber method that calls negative acknowledge with a custom redelivery delay, **When** the negative acknowledgment is processed, **Then** the message is redelivered after the specified delay.

---

### User Story 3 - Disable automatic acknowledgment for manual control (Priority: P1)

As a developer, I want to configure my subscriber to disable automatic acknowledgment so that I have full control over when messages are acknowledged or negative acknowledged.

**Why this priority**: This is foundational functionality that enables all explicit ack/nak scenarios. Without disabling automatic acknowledgment, developers cannot implement custom ack/nak logic.

**Independent Test**: A Quarkus application with a `@NatsSubscriber` method configured with automatic acknowledgment disabled can be verified to not automatically acknowledge messages.

**Acceptance Scenarios**:

1. **Given** a subscriber method with automatic acknowledgment disabled, **When** the method completes without explicitly acknowledging, **Then** the message remains in the consumer's pending list (not acknowledged).
2. **Given** a subscriber method with automatic acknowledgment enabled (default), **When** the method completes successfully, **Then** the message is automatically acknowledged.
3. **Given** a subscriber method that switches from automatic to manual acknowledgment, **When** a message is processed, **Then** the developer must explicitly acknowledge or negative acknowledge the message.

---

### User Story 4 - Use ack/nak in different error handling scenarios (Priority: P2)

As a developer, I want to use explicit acknowledgment and negative acknowledgment to implement different error handling strategies (retry, dead-letter queue, logging) based on the type of error encountered.

**Why this priority**: This provides flexibility for implementing complex error handling workflows. It enables developers to distinguish between recoverable errors (retry via nak) and unrecoverable errors (ack and log).

**Independent Test**: A Quarkus application with a `@NatsSubscriber` method that handles different error types and uses appropriate ack/nak responses can be tested to verify correct routing of messages based on error type.

**Acceptance Scenarios**:

1. **Given** a subscriber method processing a message with a transient error, **When** the method negative acknowledges the message, **Then** the message is redelivered for retry.
2. **Given** a subscriber method processing a message with a permanent error, **When** the method acknowledges the message and logs the error, **Then** the message is not redelivered and the error is recorded.
3. **Given** a subscriber method with a timeout error, **When** the method negative acknowledges with an extended delay, **Then** the message is redelivered after the extended delay.

---

### Edge Cases

- What happens if a subscriber method calls both acknowledge and negative acknowledge on the same message? The first call should succeed; subsequent calls should be idempotent (silently ignored).
- What happens if a subscriber method neither acknowledges nor negative acknowledges before the method returns? The framework does nothing; the message handling behavior is determined by NATS JetStream's configured AckPolicy and timeout settings.
- What happens if a subscriber method calls acknowledge but then throws an exception after calling acknowledge? The acknowledge should have already taken effect; the exception should not prevent the acknowledgment.

## Assumptions

- A NATS JetStream server is running with message acknowledgment support.
- A durable consumer is configured on a NATS stream (this feature builds on top of feature 008-durable-nats-consumers).
- Message acknowledgment in NATS JetStream works via AckPolicy settings (explicit, all, none).
- The application is using Quarkus with the EasyNATS extension.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `@NatsSubscriber` annotation MUST include an `autoAck` property (default: `true`) to enable or disable automatic acknowledgment of messages.
- **FR-002**: When `autoAck` is set to `false`, the framework MUST NOT automatically acknowledge received messages; control passes to the subscriber method.
- **FR-003**: The framework MUST provide an acknowledgment API that allows subscriber methods to explicitly acknowledge a message.
- **FR-004**: The framework MUST provide a negative acknowledgment (nak) API that allows subscriber methods to explicitly reject a message.
- **FR-005**: The nak API MUST accept an optional redelivery delay parameter to specify how long the broker should wait before redelivering the message.
- **FR-006**: Calling acknowledge on an already-acknowledged message MUST be idempotent (does not cause an error).
- **FR-007**: Calling negative acknowledge on an already-negative-acknowledged message MUST be idempotent (does not cause an error).
- **FR-008**: The acknowledgment/nak API MUST be accessible within the context of a subscriber method invocation.
- **FR-009**: The framework MUST properly handle the case where a subscriber method throws an exception after calling acknowledge or nak; the ack/nak action should already have taken effect.
- **FR-010**: The framework MUST validate that a durable consumer is configured with an appropriate `AckPolicy` (e.g., `explicit`) when manual ack/nak control is used.

### Key Entities

- **Subscriber Method Context**: The execution context associated with a subscriber method invocation that provides access to ack/nak APIs. This includes the message, headers, and control functions.
- **Acknowledgment API**: An interface or method that allows explicit acknowledgment of a message within a subscriber method.
- **Negative Acknowledgment API**: An interface or method that allows explicit rejection of a message with optional redelivery delay within a subscriber method.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can disable automatic acknowledgment by setting `autoAck = false` in the `@NatsSubscriber` annotation.
- **SC-002**: A developer can acknowledge a message by calling the acknowledgment API from within a subscriber method.
- **SC-003**: A developer can negative acknowledge a message with an optional redelivery delay by calling the nak API from within a subscriber method.
- **SC-004**: In end-to-end tests, messages are not redelivered when explicitly acknowledged.
- **SC-005**: In end-to-end tests, messages are redelivered after the specified delay when explicitly negative acknowledged.
- **SC-006**: In end-to-end tests, acknowledgment and negative acknowledgment operations are idempotent (repeated calls do not cause errors or unexpected behavior).
- **SC-007**: The API for ack/nak control is documented with clear examples showing how to implement error handling scenarios.

## Notes

- This feature builds on top of feature 008-durable-nats-consumers, which provides durable consumer support.
- Ack/nak control is particularly useful for implementing complex error handling workflows and retry patterns.
- The design should consider how ack/nak APIs are made available to subscriber methods (e.g., injection, parameter, context variable).
