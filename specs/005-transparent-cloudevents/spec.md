# Feature Specification: Transparent CloudEvent Publisher

**Feature Branch**: `005-transparent-cloudevents`
**Continues**: `004-nats-subscriber-mvp`
**Created**: 2025-10-26
**Status**: Draft
**Input**: User description: "as a developer I want to send typed objects using NatsPublisher, that transparent to me, encodes the message in CloudEvent format... ie NatsPublisher.publish(payload) is sendt encoded as cloudevent, removing the need for NatsPublisher.publishCloudEvent methods. All required headers for cloudevent is just auto generated for me"

## Clarifications

### Session 2025-10-26
- Q: What should happen if the payload object cannot be serialized to JSON? → A: Throw a runtime exception

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Publish POJO as CloudEvent (Priority: P1)

As a developer, I want to publish a Plain Old Java Object (POJO) using a simple `NatsPublisher.publish("subject", payload)` method. I expect the framework to automatically wrap my POJO in a CloudEvents envelope, generate all required attributes, and then send it to the NATS server. This simplifies my application code by removing the need to manually construct CloudEvents or call a specialized method like `publishCloudEvent`.

**Why this priority**: This is the core of the feature. It dramatically simplifies the publisher API, reduces boilerplate code, and ensures that all messages conform to a standard, interoperable format by default.

**Independent Test**: A developer can inject a `NatsPublisher`, create a simple POJO instance, and call the standard `publish` method. A separate NATS client can subscribe to the subject and verify that the received message contains the appropriate CloudEvents headers and that its data payload is the correctly serialized POJO.

**Acceptance Scenarios**:

1. **Given** a `NatsPublisher` instance and a `User` POJO.
   **When** a developer calls `publisher.publish("users.registered", userObject)`.
   **Then** a message is published to the "users.registered" subject, its headers contain valid CloudEvents attributes (`specversion`, `id`, `source`, `type`, `datacontenttype`), and its data payload is the JSON-serialized `userObject`.

2. **Given** the `quarkus.easynats.cloudevents.source` property is configured in `application.properties`.
   **When** any POJO is published using the `NatsPublisher`.
   **Then** the `source` attribute in the resulting CloudEvent message headers matches the configured value.

### Edge Cases

- **Serialization Failure**: If the provided payload object cannot be serialized, the `publish` method will throw a `SerializationException`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `NatsPublisher.publish(String subject, Object payload)` method MUST automatically encode the provided payload into the CloudEvent format.
- **FR-002**: The framework MUST transport CloudEvents attributes as NATS message headers.
- **FR-003**: The framework MUST automatically generate all mandatory CloudEvents attributes:
    - `specversion`: MUST be "1.0".
    - `id`: MUST be a unique identifier (e.g., UUID).
    - `source`: MUST be the value of `quarkus.application.name`.
    - `type`: MUST be determined by the payload's class. For complex POJOs, it is the fully qualified class name (e.g., `com.example.User`). For native types, it is the simple class name (e.g., `byte[]`, `java.lang.String`).
    - `datacontenttype`: MUST be `application/json` for POJOs. For native types, it MUST be `text/plain`, with the payload encoded as a string (or Base64 for `byte[]`).
- **FR-004**: The framework MUST use the logic from `TypedPayloadEncoder` to differentiate between native types and complex POJOs to determine the correct encoding and `type`/`datacontenttype` headers.
- **FR-005**: The existing `publishCloudEvent` methods on `NatsPublisher` MUST be removed.
- **FR-006**: The `publish` method MUST throw a `SerializationException` if the payload object cannot be serialized.

### Key Entities

- **NatsPublisher**: The CDI bean used by developers to send messages to NATS subjects.
- **POJO Payload**: The typed Java object that represents the business data being published.
- **CloudEvent**: The standardized envelope format used to wrap the POJO payload, with metadata stored in NATS headers.

### Assumptions

- The default and primary payload serialization format is JSON (UTF-8).
- The primary use case is for developers who want CloudEvents by default, not as an exception.
- The simplification of the `NatsPublisher` API is a primary goal.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can publish a typed object that is received by any standard CloudEvents-aware subscriber as a valid CloudEvent, without writing any CloudEvent-specific code in the publisher.
- **SC-002**: The public API surface of `NatsPublisher` related to publishing is simplified by removing specialized `publishCloudEvent` methods in favor of a single, intelligent `publish` method.
- **SC-003**: All POJO messages published through the framework automatically and consistently conform to the CloudEvents specification version 1.0, improving interoperability.