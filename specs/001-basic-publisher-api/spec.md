# Feature Specification: Basic NatsPublisher API (MVP)

**Feature Branch**: `001-basic-publisher-api`
**Created**: 2025-10-25
**Status**: Draft
**Input**: User description: "Create untyped NatsPublisher as start, without cloudevent format. Only just enough code to post a 'hello' string to a subject called 'test'. keep it simple."

## User Scenarios & Testing

### User Story 1 - Inject and Use a Basic String Publisher (Priority: P1)

A developer embeds the Quarkus EasyNATS extension in their application and wants to publish a simple string message to a NATS subject without CloudEvents wrapping.

**Why this priority**: This is the absolute minimum viable publisher experience. It demonstrates the core injection pattern and proves the extension can send messages to NATS.

**Independent Test**: Can be fully tested by creating a minimal Quarkus app with the extension, injecting a `NatsPublisher`, calling `publish("hello")`, and verifying the message appears in a local NATS broker.

**Acceptance Scenarios**:

1. **Given** a Quarkus application with EasyNATS extension loaded, **When** a developer injects `@Inject NatsPublisher publisher` on a field, **Then** the extension provides a working publisher instance
2. **Given** an injected publisher instance, **When** developer calls `publisher.publish("hello")`, **Then** the message is sent to the default subject
3. **Given** a publisher sending to NATS, **When** a message is published, **Then** a connected NATS client can receive the message from the subject `test`
4. **Given** a message published via the publisher, **When** it arrives at NATS, **Then** it contains exactly the string "hello" with no modifications

### Edge Cases

- What happens if NATS broker is unreachable at startup? (Deferred: error handling strategy TBD in next MVP)
- What if publisher is called before NATS connects? (Deferred: lifecycle/blocking semantics TBD in next MVP)

## Requirements

### Functional Requirements

- **FR-001**: Extension MUST provide a `NatsPublisher` class that can be injected via CDI
- **FR-002**: Injected publisher MUST have a `publish(String message)` method
- **FR-003**: Publisher `publish()` method MUST send the string to NATS subject `test`
- **FR-004**: Message content MUST be the raw string, no CloudEvents wrapping
- **FR-005**: Extension MUST auto-initialize a singleton NATS connection on application startup

### Key Entities

- **NatsPublisher**: Simple wrapper around NATS JetStream publish API
  - Method: `publish(String message)` → sends to subject
  - Subject (hardcoded for MVP): `test`
  - No type parameters (untyped)
  - No CloudEvents support in this MVP

## Success Criteria

### Measurable Outcomes

- **SC-001**: Integration test passes: inject publisher, publish "hello", receive message from subject `test` on local NATS broker
- **SC-002**: Extension builds and runs with Quarkus 3.27.0, Java 21
- **SC-003**: No external dependencies added to runtime module beyond Quarkus Arc and JNats
- **SC-004**: Code coverage >= 80% for new publisher code

## Constraints & Assumptions

- **Tech Stack**: Java 21, Quarkus 3.27.0, NATS JetStream client (io.nats:jnats)
- **Scope**: Publisher only; subscriber deferred to future MVP
- **Subject**: Hardcoded to `test` for simplicity; subject configuration deferred
- **Message Type**: Strings only; typed generics deferred
- **CloudEvents**: Not supported in this MVP
- **Target Platform**: Linux/macOS with local NATS broker (Docker or binary)

## Out of Scope (for this MVP)

- Subscriber functionality
- CloudEvents format support
- Message type generics/type safety
- Subject configuration via annotations
- Consumer strategy / durable subscriptions
- Health checks / observability
- Error recovery / reconnection logic
