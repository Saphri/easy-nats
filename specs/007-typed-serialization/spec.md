# Feature Specification: Typed Serialization with Jackson Integration

**Feature Branch**: `007-typed-serialization`
**Continues**: `006-typed-subscriber`
**Created**: 2025-10-27
**Status**: Draft
**Input**: User description: "as a library maintainer I want to simplify the decoding/encoding of messages, so its less magic for users. Only typed objects will be supported. If it cant be used with Jackson its not supported, and documentation should suggest the user create a wrapper if they have the need"

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

### User Story 1 - Publish and Subscribe to Typed Messages (Priority: P1)

A library user defines a message type as a simple class (POJO or record) and publishes/subscribes to instances of that type. The library automatically handles serialization and deserialization—users just work with typed objects.

**Why this priority**: This is the core value proposition of the feature. Users focus on their domain types, not on serialization mechanics.

**Independent Test**: Can be fully tested by defining a POJO type, publishing an instance, and verifying it arrives as the correct type on the subscriber, without manually handling any serialization.

**Acceptance Scenarios**:

1. **Given** a user defines a POJO `OrderData(String id, BigDecimal amount)`, **When** they publish an instance via a typed publisher, **Then** the message is sent to NATS and the user does not need to manually serialize or handle any encoding details
2. **Given** a subscriber is listening for `OrderData` messages, **When** a message arrives from NATS, **Then** it is automatically deserialized to an `OrderData` instance without the user handling JSON or any transport-level details
3. **Given** a message arrives that cannot be deserialized to the target type, **When** deserialization is attempted, **Then** the error is logged with the target type and details to help the user debug

---

### User Story 2 - Only Jackson-Compatible Types Are Supported (Priority: P1)

A library user attempts to use an unsupported message type (e.g., a primitive like `int`, an array, or a class without a no-arg constructor). The library clearly rejects or fails with messages explaining what types are supported and how to wrap unsupported types.

**Why this priority**: This is critical to the "less magic" goal. The rule is simple and explicit: if Jackson can serialize/deserialize it, the library supports it; if not, users wrap it. No special cases or alternative encodings.

**Independent Test**: Can be fully tested by attempting to use unsupported types (primitives, arrays, custom classes) and verifying clear error messages guide the user to wrap them.

**Acceptance Scenarios**:

1. **Given** a user tries to publish a typed message with type `int`, **When** the publisher validates the type at registration, **Then** a clear error indicates primitives are not supported and suggests wrapping in a POJO
2. **Given** a user tries to subscribe to a type with an unresolvable generic parameter, **When** the subscriber registers, **Then** Jackson's type introspection fails with a clear error indicating the type cannot be resolved
3. **Given** a user tries to deserialize JSON into a type without a no-arg constructor, **When** deserialization is attempted, **Then** the error log indicates the type needs a no-arg constructor or custom Jackson deserializer, with documentation link to the wrapper pattern

---

### User Story 3 - Support Common Jackson Annotations for Type Customization (Priority: P2)

A library user has a message type with fields that need custom JSON serialization (e.g., `@JsonProperty` for field name mapping, `@JsonIgnore` for transient fields, `@JsonDeserialize` for custom deserializers). The library respects Jackson annotations, allowing users to customize serialization without library-specific magic.

**Why this priority**: Users with existing Jackson types should be able to reuse them without modification. This reduces friction and makes the library predictable—it follows Jackson conventions.

**Independent Test**: Can be fully tested by defining a POJO with Jackson annotations, publishing/subscribing, and verifying the annotations are respected (e.g., a field with `@JsonIgnore` doesn't appear in the message).

**Acceptance Scenarios**:

1. **Given** a POJO with `@JsonProperty("custom_name")` on a field, **When** the message is serialized to JSON, **Then** the JSON uses "custom_name" as the key, not the Java field name
2. **Given** a POJO with `@JsonIgnore` on a field, **When** the message is serialized, **Then** that field is excluded from the JSON payload
3. **Given** a POJO with `@JsonDeserialize(using = CustomDeserializer.class)`, **When** the message is deserialized, **Then** the custom deserializer is invoked

---

### User Story 4 - Generate Clear Documentation on Supported Types (Priority: P2)

Library documentation explicitly lists what makes a type Jackson-compatible and provides examples of:
- Supported types (records, POJOs with no-arg constructors, types with custom deserializers)
- Unsupported patterns (types without no-arg constructors, custom serialization not declared to Jackson)
- How to create wrappers for unsupported types

**Why this priority**: Users need clear guidance on what's supported. This prevents confusion and support burden. It's part of the "less magic" philosophy—the rules are explicit.

**Independent Test**: Can be validated by verifying documentation exists, covers the cases, and examples are accurate and runnable.

**Acceptance Scenarios**:

1. **Given** a user reads the documentation, **When** they see a list of Jackson requirements, **Then** they understand what types are supported without reading Jackson docs
2. **Given** a user has an unsupported type, **When** they read the wrapper section, **Then** they have a clear pattern for wrapping the type
3. **Given** the documentation, **When** a user searches for "serialization," **Then** they find guidance on Jackson compatibility and wrapper patterns

### Edge Cases

- What happens when a field is `null` in a POJO being serialized? (Jackson's default null handling applies; typically omits null fields or includes them based on configuration)
- What if a user tries to publish/subscribe with a primitive type like `int` or array like `int[]`? (Type validation at registration rejects these with a clear error directing to the wrapper pattern)
- What if a user defines two subscribers with different type expectations for the same subject? (Both work independently; each deserializes to their declared type)
- How are deserialization errors (malformed JSON, type mismatch) reported? (Errors logged with target type, raw payload, and root cause for debugging)

## Clarifications

### Session 2025-10-27

- Q: What logging format should be used for serialization/deserialization errors (free-form vs. structured)? → A: Free-form, human-readable log messages with type context and root cause

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST automatically serialize user-provided objects when publishing typed messages (using the default CDI-injected ObjectMapper)
- **FR-002**: System MUST automatically deserialize received messages to the declared type when subscribing (using the default CDI-injected ObjectMapper)
- **FR-003**: System MUST respect Jackson annotations (`@JsonProperty`, `@JsonIgnore`, `@JsonDeserialize`, etc.) during serialization/deserialization
- **FR-004**: System MUST reject unsupported types (primitives, arrays, types without no-arg constructors) at publisher/subscriber registration using Jackson type introspection
- **FR-005**: System MUST provide clear, actionable error messages when serialization/deserialization fails, logged as free-form human-readable text at ERROR level, including target type, Jackson root cause, and debugging context
- **FR-006**: Documentation MUST define what types are Jackson-compatible and what are not (with examples)
- **FR-007**: Documentation MUST provide a clear wrapper pattern for users with unsupported types

### Key Entities *(include if feature involves data)*

- **Jackson-Compatible Type**: A Java class that Jackson can serialize/deserialize (POJOs with no-arg constructors, records, types with explicit Jackson annotations). Primitives and arrays are NOT supported.
- **Type Wrapper**: A Jackson-compatible POJO created by the user to wrap an unsupported type (e.g., wrapping `int` in `IntValue(int value)`), making it compatible with the library

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Users can publish and subscribe to Jackson-compatible types (POJOs, records) by simply declaring the type—no serialization code required
- **SC-002**: Unsupported types (primitives, arrays, types without no-arg constructors) are rejected at publisher/subscriber registration with clear error messages directing to the wrapper pattern
- **SC-003**: Deserialization errors at runtime are logged with the target type and root cause (e.g., "Missing no-arg constructor for OrderData")
- **SC-004**: Documentation includes at least 3 examples of Jackson-compatible types and at least 2 examples of wrapper patterns for unsupported types
- **SC-005**: Users with existing Jackson-annotated types can use them without modification
- **SC-006**: A developer new to the library can successfully publish/subscribe to typed messages following documentation, without understanding Jackson internals

## Assumptions

1. **Default CDI-injected ObjectMapper only**: The library uses only the default ObjectMapper provided by Quarkus CDI. No support for custom ObjectMapper instances or configuration. Users cannot customize the mapper.
2. **Jackson-only serialization**: All typed messages use Jackson for JSON serialization (internal detail). No alternative encodings. Unsupported types must be wrapped by the user.
3. **CloudEvents binary-mode (internal)**: Messages are wrapped in CloudEvents binary-mode format for NATS delivery (internal implementation detail users do not interact with).
4. **No instantiation for validation**: The library cannot instantiate user classes to validate them. Type validation uses Jackson's type introspection (via `ObjectMapper.getTypeFactory().constructType()`) at subscriber/publisher registration, with runtime errors for failures at first use.
5. **Standard Jackson behavior**: The library does not override Jackson's default null handling, field inclusion, or other behaviors. Users get standard Jackson semantics and can customize via Jackson annotations.
6. **Error handling via logging**: Serialization/deserialization errors are logged as free-form, human-readable messages with ERROR log level, including target type, root cause from Jackson exception, and relevant context (payload summary, target method/subject) for debugging. No structured logging format enforced; implementation focuses on clarity for developers reading logs.
