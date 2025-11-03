# Feature Specification: Custom Payload Codec

**Feature Branch**: `018-custom-payload-codec`
**Created**: 2025-11-03
**Status**: Draft
**Input**: User description: "As a developer I want to be able to easy provide my own payload encode/decoder together with type validator, hence override the seamless jackson/cloudevent that is default"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register Custom Codec (Priority: P1)

As a developer, I want to register a custom encoder/decoder (codec) for a specific payload type, so that I can control the serialization and deserialization logic, bypassing the default JSON/CloudEvent format.

**Why this priority**: This is the core functionality of the feature. It enables developers to integrate their own serialization formats like Protobuf, Avro, or other binary formats, which is crucial for performance-sensitive applications or for integrating with existing systems.

**Independent Test**: A test can be created where a custom codec is registered for a `Product` class. The test will publish a `Product` object using `NatsPublisher<Product>` and a `@NatsSubscriber` will receive it. The test will verify that the custom codec's `encode` and `decode` methods were invoked.

**Acceptance Scenarios**:

1.  **Given** a developer has defined a custom `Codec` implementation for a `Product` class, **When** they register it with the EasyNATS system, **Then** any `NatsPublisher<Product>` instance MUST use this codec for serialization.
2.  **Given** a custom codec for the `Product` class is registered, **When** a `@NatsSubscriber` method expecting a `Product` object receives a message, **Then** the custom codec MUST be used for deserialization.

---

### User Story 2 - Perform Payload Validation (Priority: P2)

As a developer, I want my custom codec to be able to perform validation during deserialization, so that invalid payloads are rejected before they reach my business logic.

**Why this priority**: This allows for data integrity and validation to be handled at the edge of the application, preventing invalid data from propagating through the system. It's a common requirement in robust messaging architectures.

**Independent Test**: A test can be created where a custom codec's `decode` method is designed to throw a `ValidationException` for certain payloads. The test will publish a message that fails this validation and verify that the `@NatsSubscriber` method is not invoked and the message is negatively acknowledged (NACKed).

**Acceptance Scenarios**:

1.  **Given** a custom codec with validation logic is registered, **When** an incoming message payload fails validation, **Then** the corresponding `@NatsSubscriber` method MUST NOT be invoked.
2.  **Given** a message fails validation and the codec throws a specific validation exception, **When** this occurs, **Then** the system MUST log the error and the message MUST be negatively acknowledged (NACKed) to prevent reprocessing if it's a persistent stream.

### Edge Cases

-   What happens if a codec is registered for a type that is a superclass of the published type?
-   How does the system behave if the `encode` method of a custom codec returns `null` or an empty byte array?
-   How does the system handle a `decode` method that throws an unexpected (non-validation) exception?
-   What happens if two different codecs are registered for the same type?

## Requirements *(mandatory)*

### Functional Requirements

-   **FR-001**: The system MUST provide a public interface for developers to implement their own payload codecs.
-   **FR-002**: The system MUST provide a mechanism to register a custom codec implementation for a specific Java type.
-   **FR-003**: When a `NatsPublisher` sends a message of a type that has a registered custom codec, it MUST use that codec to encode the payload.
-   **FR-004**: When a `@NatsSubscriber` is configured to receive a type that has a registered custom codec, the system MUST use that codec to decode the incoming message payload.
-   **FR-005**: If no custom codec is registered for a given type, the system MUST default to the existing Jackson/CloudEvents serialization mechanism.
-   **FR-006**: The codec interface MUST support throwing exceptions to signal decoding or validation failures.
-   **FR-007**: The system MUST gracefully handle exceptions thrown by a custom codec during deserialization, preventing the subscriber method from being called and ensuring the message is not automatically acknowledged.

### Key Entities *(include if feature involves data)*

-   **Codec**: An interface that developers can implement. It will define methods for encoding and decoding, such as `byte[] encode(T object)` and `T decode(byte[] data, Class<T> type)`.
-   **CodecRegistry**: An internal component responsible for managing the registration of custom codecs and providing the correct codec for a given Java type at runtime.

## Success Criteria *(mandatory)*

### Measurable Outcomes

-   **SC-001**: A developer can fully replace the default JSON serialization with a custom format (e.g., a simple string-based format) for a specific message type by implementing one interface and writing a single line of registration code.
-   **SC-002**: The lookup and invocation overhead of the custom codec mechanism MUST add less than 5% latency compared to the baseline default serialization, measured at the 95th percentile.
-   **SC-003**: For any given type, if a custom codec is registered, 100% of messages of that type published via `NatsPublisher` MUST be serialized by that codec.
-   **SC-004**: When a custom codec's `decode` method throws an exception, 100% of those messages MUST be prevented from reaching the `@NatsSubscriber`'s business logic.