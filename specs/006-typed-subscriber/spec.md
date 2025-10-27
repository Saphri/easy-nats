# Feature Specification: Typed Subscriber with @NatsSubscriber Annotation

**Feature Branch**: `006-typed-subscriber`
**Created**: 2025-10-27
**Status**: Draft
**Input**: User description: "as a developer I want to receive typed objects with @NatsSubscriber annotated methods"

## Clarifications
### Session 2025-10-27
- Q: How should the system handle a message that does not conform to the CloudEvents 1.0 binary-mode format, as mentioned in requirement FR-004? → A: Log an error and negatively acknowledge (`nak`) the message, allowing NATS to redeliver it.
- Q: How should a developer provide a custom Jackson `ObjectMapper` for deserialization, as mentioned in requirement FR-007? → A: The extension should automatically inject a user-provided `ObjectMapper` CDI bean if one is defined in the application context.
- Q: When a deserialization failure occurs, what specific information should be logged according to requirement FR-006? → A: Subject, method name, exception.
- Q: Besides the deserialized payload, should a subscriber method be able to receive message metadata (like the subject or headers)? → A: No, the method should only receive the typed payload to keep the signature clean.
- Q: How should the `@NatsSubscriber` annotation handle multiple subjects for a single method? → A: Multiple subjects are not supported. Only one `@NatsSubscriber` annotation is allowed on a method. Developers should use wildcards in subjects for hierarchical subscriptions.

## Dependencies

This feature builds on top of `specs/004-nats-subscriber-mvp/` which provides:
- Basic `@NatsSubscriber` annotation infrastructure
- Ephemeral consumer creation and lifecycle management
- Implicit ack/nak mechanism (success → ack, exception → nak)
- Build-time bean discovery and wiring via Quarkus @BuildStep
- Method signature validation at build time
- Connection handling and error logging

This feature adds:
- **CloudEvents 1.0 unwrapping** (mandatory for all messages)
- **Typed message deserialization** (POJO, records, generics instead of String-only)
- **CloudEvents data extraction** into typed parameters

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Receive Typed Messages via Annotated Methods (Priority: P1)

As a developer, I want to use the `@NatsSubscriber` annotation on methods to automatically receive and deserialize strongly-typed message objects from a NATS subject, so that I can handle domain objects directly without manual serialization/deserialization.

**Why this priority**: This is the core feature that enables the entire typed subscriber pattern. Without this capability, the annotation is meaningless. It's the foundation for all other subscriber functionality.

**Independent Test**: Create a simple Quarkus application that:
1. Defines a subscriber method annotated with `@NatsSubscriber` receiving a specific POJO type
2. Publishes a JSON message to that subject
3. Verifies the method receives the correctly deserialized object instance

**Acceptance Scenarios**:

1. **Given** a method annotated with `@NatsSubscriber(subject = "orders")` expecting an `Order` type, **When** a JSON message matching the Order structure is published to "orders", **Then** the method receives an Order instance with all fields correctly deserialized.

2. **Given** a subscriber method expecting type `User`, **When** a message with UserID and Name fields is published, **Then** the method receives a User object, not a raw string or Map.

---

### User Story 2 - Unwrap and Deserialize CloudEvents Data (Priority: P1)

As a developer, I want subscriber methods to automatically unwrap CloudEvents-wrapped messages (per CloudEvents 1.0 specification) and deserialize the event data into my typed objects, so that I receive domain objects directly without having to parse the CloudEvent envelope myself.

**Why this priority**: This is a core requirement, not optional. All messages in this system are CloudEvent-wrapped. Subscribers MUST handle CloudEvents format; generic JSON messages are not supported. This is essential for consistency with the transparent CloudEvents publisher.

**Independent Test**:
1. Publish a CloudEvents-formatted message with a User object as the event data
2. Verify that a subscriber method receives the User object deserialized from the CloudEvent's data field (not the CloudEvent wrapper)

**Acceptance Scenarios**:

1. **Given** a subscriber method expecting type `User`, **When** a CloudEvents message with User data is published, **Then** the method receives a User instance deserialized from the event's data field.

2. **Given** a CloudEvents message with nested JSON data matching the expected type, **When** the message is delivered to a subscriber, **Then** the data is correctly deserialized to the target type.

---

### User Story 3 - Auto-Inject Subscriber Bean into Application (Priority: P2)

As a developer, I want the Quarkus extension to automatically discover, register, and inject `@NatsSubscriber` annotated beans, so that I don't need to manually manage subscriber lifecycle or wiring.

**Why this priority**: Required for the DX goal of "zero-config" - developers should only need the annotation. Depends on P1 (the annotation itself must work first).

**Independent Test**:
1. Define a Quarkus bean with @NatsSubscriber methods
2. Inject it into another bean or test
3. Verify the bean is properly registered and receives messages

**Acceptance Scenarios**:

1. **Given** a Quarkus application with a bean containing `@NatsSubscriber` methods, **When** the application starts, **Then** the subscriber bean is automatically instantiated and connected to its subjects.

2. **Given** a service that needs to use a subscriber bean, **When** it injects the subscriber via `@Inject`, **Then** the injection succeeds and the bean is available.

---

### Edge Cases

- What happens when a CloudEvents message arrives but the data field cannot be deserialized to the target type?
- What happens when a non-CloudEvents message arrives on a subscribed subject?
- How does the subscriber handle connection loss or temporary NATS unavailability?
- What happens if the same method is subscribed to multiple subjects?
- Can a developer override the deserialization behavior (e.g., use a custom Jackson ObjectMapper)?
- What if a subscriber method throws an exception during message processing?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

**Inherited from 004-nats-subscriber-mvp** (assumed working):
- FR-101: `@NatsSubscriber` annotation exists and is discoverable
- FR-102: Build-time method signature validation (404 checks that methods have correct parameters)
- FR-103: Build-time bean discovery and wiring
- FR-104: Implicit ack/nak mechanism based on method success/exception

**New in this feature**:
- **FR-001**: System MUST automatically unwrap CloudEvents 1.0 binary-mode messages and extract the event data from payload for deserialization
- **FR-002**: System MUST support all types that `TypedPayloadEncoder` supports when sending: primitive wrappers, String, byte types, primitive arrays, String arrays, POJOs, records, generic types
- **FR-003**: System MUST deserialize the CloudEvents data field into the method's parameter type using appropriate decoder strategy:
  - Native types (primitives, String, byte arrays): Direct decoding or base64/space-separated parsing
  - Complex types (POJOs, records, generics): Jackson ObjectMapper JSON deserialization
- **FR-004**: System MUST reject non-CloudEvents messages (messages that don't conform to CloudEvents 1.0 binary-mode format) by logging an error and negatively acknowledging (`nak`) the message.
- **FR-005**: System MUST perform build-time validation of parameter types (ensure type is a supported TypedPayloadEncoder type)
- **FR-006**: System MUST log deserialization errors at ERROR level, including the NATS subject, subscriber method name, and the exception details, but not the message payload.
- **FR-007**: System MUST use a user-provided `ObjectMapper` CDI bean if one is available in the application context; otherwise, it MUST use the default Quarkus `ObjectMapper`.
- **FR-008**: System MUST decode native types using the same strategy as TypedPayloadEncoder encodes them: base64 for byte types, space-separated for primitive arrays, comma-separated for String arrays

### Key Entities

- **@NatsSubscriber Annotation**: Marks a method as a message subscriber. It specifies a single subject, which may include NATS wildcards (`*`, `>`), and provides typing information.
- **Subscriber Bean**: A Quarkus CDI bean containing one or more @NatsSubscriber methods
- **Message Payload**: The deserialized data object delivered to the subscriber method

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

**Core typed/CloudEvents functionality**:
- **SC-001**: A CloudEvents message with User object data is received, automatically unwrapped, and the User data is deserialized into a User parameter (testable via integration test)
- **SC-002**: Subscribers can receive typed objects (POJO, records, generics) directly in method parameters, with automatic JSON deserialization from CloudEvents data field (testable via integration test with 3+ different types)
- **SC-003**: Multiple typed parameters are not supported; methods have exactly one parameter and it receives the deserialized CloudEvents data (testable via build-time validation)

**Error handling**:
- **SC-004**: Non-CloudEvents messages are nacked and logged appropriately (testable via integration test sending raw JSON or malformed CloudEvent)
- **SC-005**: Deserialization failures (JSON doesn't match type) are nacked and logged with error details (testable via integration test sending CloudEvent with incompatible data)

**Build-time validation**:
- **SC-006**: Build fails with clear error if parameter type is not Jackson-deserializable (testable via compilation test)

**Integration with 004-nats-subscriber-mvp**:
- **SC-007**: Implicit ack/nak still works: successful method execution acknowledges, exceptions cause nack (testable via integration test observing message redelivery)

## Assumptions

1. **CloudEvents Binary-Mode Only**: All messages MUST be CloudEvents 1.0 **binary-mode** format (attributes in NATS headers with `ce-` prefix, data in payload). Structured-mode (entire CloudEvent in payload) is NOT supported. Generic JSON messages are NOT supported. This is a design constraint.
   - Reference: https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md
   - Binary-mode: Attributes in headers (`ce-specversion`, `ce-type`, etc.), data in payload
   - Structured-mode: NOT SUPPORTED (entire event envelope in payload)
2. **Event Data Format**: CloudEvent event data is assumed to be JSON string in message payload. Binary event data is out of scope.
3. **Implicit Acknowledgment Only**: This feature inherits implicit ack/nak from 004. Manual acknowledgment control via ConsumerContext is NOT included (deferred to future MVP).
4. **Ephemeral Consumers**: 004 creates ephemeral consumers; this feature maintains that behavior.
5. **Method Signature**: Methods must be public with exactly one parameter: the typed data object. No message metadata (e.g., headers, subject) will be passed as additional parameters.
6. **ObjectMapper**: Uses the default Jackson ObjectMapper provided by Quarkus. Custom ObjectMapper injection follows standard Quarkus patterns.
7. **Error Handling**: On method exception, the message is nacked (inherited from 004). On deserialization failure, message is also nacked.
8. **Build-Time Validation**: Parameter types must be Jackson-deserializable (new validation in this feature).
9. **Single Instance**: One subscriber bean instance per application (inherited from 004).
10. **Foundation**: All 004-nats-subscriber-mvp infrastructure (annotation discovery, bean wiring, connection handling) is assumed to be working correctly.
11. **Single Subject per Annotation**: The `@NatsSubscriber` annotation supports only a single subject string. To subscribe to multiple subjects, developers should use NATS wildcards (`*`, `>`). Multiple annotations on a single method are not supported.