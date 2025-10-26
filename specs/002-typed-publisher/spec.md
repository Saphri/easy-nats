# Feature Specification: Typed NatsPublisher with CloudEvents Support

**Feature Branch**: `002-typed-publisher`
**Created**: 2025-10-26
**Status**: Draft
**Input**: Add typed support to NatsPublisher with JSON payloads using Jackson and CloudEvents headers

## Clarifications

### Session 2025-10-26

- Q: How should non-serializable objects be handled? → A: Throw checked `SerializationException` with user-friendly message
- Q: Which CloudEvents fields are required vs. optional? → A: `ce_type` and `ce_source` are optional; auto-generated from fully-qualified class name and hostname if not provided; `ce_id` (UUID) and `ce_time` (ISO 8601) always auto-generated
- Q: What should happen when publishing null objects? → A: Throw validation exception (e.g., `IllegalArgumentException("Cannot publish null object")`)
- Q: How are CloudEvents attributes transported? → A: Headers-only: CloudEvents attributes are exclusively in NATS headers. The payload is the user's object as JSON.
- Q: What should happen if a NATS subject exceeds 256 characters? → A: Allow underlying client exception to propagate.
- Q: Can developers override CloudEvents headers on a per-publish basis? → A: No, disallow overrides. Headers are always auto-generated for consistency.
- Q: When should the system check for a missing Jackson dependency? → A: At build time; the build must fail if Jackson is required but not present.

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

### User Story 1 - Publish Typed Objects as JSON (Priority: P1)

Application developers want to publish domain objects (e.g., `UserCreatedEvent`, `OrderPlaced`) to NATS as JSON messages without manually serializing them. They expect type safety at compile time and automatic JSON serialization at runtime.

**Why this priority**: This is the core value proposition. Without typed publishing, developers must handle serialization manually, introducing errors and boilerplate.

**Independent Test**: Can be fully tested by:
1. Creating a Quarkus app with the extension
2. Defining a custom domain class (e.g., `Person` with name/age fields)
3. Injecting `NatsPublisher<Person>` and calling `publisher.publish(new Person("Alice", 30))`
4. Verifying the JSON representation appears on the NATS broker subject
5. Confirming the published JSON contains all object fields and is valid

**Acceptance Scenarios**:

1. **Given** a developer has a POJO with Jackson annotations, **When** they inject `NatsPublisher<T>` for that type and publish an instance, **Then** the object is serialized to JSON and published to NATS
2. **Given** a published message on the broker, **When** inspected manually, **Then** the JSON format is human-readable and contains all public fields
3. **Given** a developer publishes multiple instances of the same type, **When** subscribers consume them, **Then** each message can be independently deserialized back to the original type

---

### User Story 2 - CloudEvents Support (Priority: P2)

Developers want to publish messages that comply with the CloudEvents specification, including metadata headers (e.g., `ce_type`, `ce_source`, `ce_subject`, `ce_id`) that describe the event. This enables interoperability with CloudEvents consumers.

**Why this priority**: CloudEvents is a CNCF standard for describing events. Support enables enterprise integration patterns and cross-system messaging.

**Independent Test**: Can be fully tested by:
1. Creating a Quarkus app with the extension
2. Publishing a message with CloudEvents metadata via `publisher.publishCloudEvent(event, "com.example/user-created", "user-service")`
3. Inspecting the NATS message headers to verify CloudEvents headers are present and correct
4. Confirming the message data (JSON payload) and headers are separate and both accessible

**Acceptance Scenarios**:

1. **Given** a developer publishes with CloudEvents metadata, **When** the message arrives on NATS, **Then** CloudEvents headers (`ce_type`, `ce_source`, `ce_specversion`, `ce_id`, `ce_time`) are set in the message headers
2. **Given** a CloudEvents publish, **When** no event ID is provided, **Then** a UUID is auto-generated for `ce_id`
3. **Given** a CloudEvents publish, **When** no timestamp is provided, **Then** the current time is set for `ce_time` in ISO 8601 format
4. **Given** a subscriber reading a CloudEvents message, **When** they extract headers, **Then** all CloudEvents standard attributes are present and properly formatted

---


### Edge Cases

- **What happens when a developer tries to publish `null`?** → Throw validation exception (e.g., `IllegalArgumentException("Cannot publish null object")`)
- **How does the system handle objects that cannot be serialized to JSON (e.g., no zero-arg constructor)?** → Throw checked `SerializationException` with user-friendly message (e.g., "Failed to serialize MyClass: missing zero-arg constructor")
- **What if a CloudEvents subject is extremely long (> 256 chars)?** → Allow underlying client exception to propagate.
- **How does the system behave if Jackson is not on the classpath?** → The build MUST fail with a clear error if Jackson is required for a given type but is not present.
- **Can developers override CloudEvents headers on a per-publish basis?** → No, this is disallowed to ensure consistency.

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST support generic `NatsPublisher<T>` where T is any Java class
- **FR-002**: System MUST encode/decode using resolution order: (1) Java primitives (int, long, byte, short, double, float, boolean, char) and `java.lang.String`, (2) arrays of primitives and String, (3) Jackson for complex types
- **FR-003**: System MUST support CloudEvents specification headers (`ce_type`, `ce_source`, `ce_specversion`, `ce_id`, `ce_time`) when publishing with CloudEvents mode
- **FR-004**: System MUST auto-generate `ce_type` from fully-qualified class name (e.g., `com.example.UserCreatedEvent`) if not provided by developer
- **FR-004b**: System MUST auto-generate `ce_source` from hostname or application identifier if not provided by developer
- **FR-005**: System MUST auto-generate `ce_id` (UUID v4) if not provided by developer
- **FR-006**: System MUST auto-generate `ce_time` (ISO 8601 UTC timestamp) if not provided
- **FR-007**: System MUST throw `IllegalArgumentException` with message "Cannot publish null object" when attempting to publish a null object instance
- **FR-008**: System MUST use full Java class names (e.g., `java.lang.String`, `com.example.UserCreatedEvent`) for CloudEvents `ce_datacontenttype`
- **FR-009**: System MUST require Jackson on the classpath for complex type serialization and fail the build with a clear error message if it is not available when needed
- **FR-010**: System MUST handle primitive type arrays (e.g., `int[]`, `String[]`) without Jackson dependency
- **FR-011**: System MUST document that complex types require `@RegisterForReflection` annotation for GraalVM native image compilation
- **FR-012**: System MUST throw checked `SerializationException` with user-friendly error message when an object cannot be serialized (e.g., "Failed to serialize MyClass: missing zero-arg constructor")
- **FR-013**: System MUST NOT perform custom validation on NATS subject length, but MUST allow exceptions from the underlying NATS client to propagate to the caller if the subject is invalid
- **FR-014**: System MUST NOT allow developers to override the auto-generated CloudEvents headers (`ce_id`, `ce_time`, `ce_source`, `ce_type`). These headers MUST always be generated by the publisher.

### Key Entities

- **NatsPublisher<T>**: Extended version of existing NatsPublisher class with generic type parameter supporting typed object publishing with JSON serialization
- **CloudEvents Headers**: A set of standard NATS message headers representing CloudEvents metadata. The message payload remains the user's unmodified JSON-serialized object.
  - `ce_type` (optional, String): Event type; auto-generated from fully-qualified class name if not provided (e.g., `com.example.UserCreatedEvent`)
  - `ce_source` (optional, String): Event source; auto-generated from hostname/application identifier if not provided
  - `ce_id` (auto-generated, String): Event ID; always generated as UUID v4
  - `ce_time` (auto-generated, String): Event timestamp; always generated in ISO 8601 UTC format

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Developers can publish typed objects as JSON without manual serialization boilerplate
- **SC-002**: CloudEvents headers are automatically set when publishing in CloudEvents mode
- **SC-003**: Primitive types (int, long, String, etc.) and arrays work without Jackson dependency
- **SC-004**: Complex types use Jackson for JSON serialization with clear error messages if Jackson unavailable
- **SC-005**: Documentation includes @RegisterForReflection guidance for native image support
- **SC-006**: Manual testing with docker-compose and NATS CLI verifies end-to-end message publishing and consumption

## Assumptions

1. **Encoder/Decoder Resolution Priority**: Primitive types (int, long, byte, short, double, float, boolean, char, String) and their arrays are handled natively without Jackson; Jackson is only used as a fallback for complex types
2. **Native Image Requirement**: Developers using complex types must annotate them with `@RegisterForReflection` (from `io.quarkus.runtime.annotations`) for GraalVM native image support
3. Jackson is the standard JSON serialization library for Quarkus (complex types only); developers are expected to include `quarkus-rest-jackson` for non-primitive types
4. CloudEvents spec version 1.0 is the target compliance level
5. Auto-generated IDs (UUID) and timestamps are acceptable defaults; developers can override if needed
6. Type information is inferred from generics at runtime (Java reflection); no additional type hints required

## Notes for Planning

- **Encoder/Decoder Resolution Order**: Type resolution follows priority: (1) Java primitives and String, (2) Primitive/String arrays, (3) Jackson for complex types
- **Primitive Type Support**: Direct encoding for int, long, byte, short, double, float, boolean, char without Jackson
- **Array Handling**: Support primitive and String arrays natively before falling back to Jackson
- **Native Image Support**: Complex types require `@RegisterForReflection` annotation for GraalVM compilation
- **Testing Approach**: Simple manual testing using docker-compose and NATS CLI (no automated integration tests initially)
- **Implementation Strategy**: Extend existing `NatsPublisher` class with generic `<T>` support (not a separate TypedNatsPublisher interface)
- Investigate Jackson configuration in Quarkus for native image compatibility
- Document CloudEvents header mapping and provide example of @RegisterForReflection usage
