# Feature Specification: Custom Payload Codec

**Feature Branch**: `018-custom-payload-codec`
**Created**: 2025-11-03
**Status**: Draft
**Input**: User description: "As a developer I want to be able to easy provide my own payload encode/decoder together with type validator, hence override the seamless jackson/cloudevent that is default"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register Global Custom Payload Codec (Priority: P1)

As a developer, I want to register a single, global encoder/decoder (codec) for the entire application, so that I can control the serialization of the event payload *within* the standard CloudEvent format, bypassing the default JSON serialization for the `data` attribute.

**Why this priority**: This is the core functionality of the feature. It enables developers to integrate their own serialization formats like Protobuf, Avro, or other binary formats, which is crucial for performance-sensitive applications or for integrating with existing systems.

**Independent Test**: A test can be created where a custom codec is registered for a `Product` class. The test will publish a `Product` object using `NatsPublisher<Product>` and a `@NatsSubscriber` will receive it. The test will verify that the custom codec's `encode` and `decode` methods were invoked.

**Acceptance Scenarios**:

1.  **Given** a developer has defined a custom `Codec` implementation, **When** they register it as a global CDI bean, **Then** any `NatsPublisher` instance MUST use this codec for serialization of all message types.
2.  **Given** a global custom codec is registered, **When** a `@NatsSubscriber` method receives a message, **Then** the global custom codec MUST be used for deserialization of all message types.

---

### User Story 2 - Perform Payload Validation (Priority: P2)

As a developer, I want my custom codec to be able to perform validation during deserialization, so that invalid payloads are rejected before they reach my business logic.

**Why this priority**: This allows for data integrity and validation to be handled at the edge of the application, preventing invalid data from propagating through the system. It's a common requirement in robust messaging architectures.

**Independent Test**: A test can be created where a custom codec's `decode` method is designed to throw a `ValidationException` for certain payloads. The test will publish a message that fails this validation and verify that the `@NatsSubscriber` method is not invoked and the message is negatively acknowledged (NACKed).

**Acceptance Scenarios**:

1.  **Given** a custom codec with validation logic is registered, **When** an incoming message payload fails validation, **Then** the corresponding `@NatsSubscriber` method MUST NOT be invoked.
2.  **Given** a message fails validation and the codec throws a specific deserialization exception, **When** this occurs, **Then** the system MUST log the error and the message MUST be negatively acknowledged (NACKed) to prevent reprocessing if it's a persistent stream.

### Edge Cases

-   **Null/empty encode result**: Developers are responsible for valid encoding; if `encode()` returns null or empty bytes, behavior is undefined. Developers should validate in their codec implementation.
-   **Unexpected exceptions from decode**: Any `DeserializationException` thrown by the codec is caught, logged (WARN), the subscriber method is not invoked, and the message is NACKed.

---

## Clarifications

### Session 2025-11-03

-   Q: Is codec registration per-instance or global? → A: Global registration at application startup (via CDI bean or configuration), not per-instance.
-   Q: How should the codec handle type matching and inheritance? → A: Exact-type matching with runtime casting. The `decode(byte[] data, Class<T> type)` method receives the target subscriber type so the developer can avoid casting errors. The framework performs a final runtime cast as a safety check.
-   Q: What exception types should the codec throw? → A: Two checked exceptions: `SerializationException` for encode failures, `DeserializationException` for decode/validation failures.
-   Q: How should validation failures be logged? → A: WARN level with exception type, message, and subject. Payload logging (truncated to ~256 bytes) is controlled by `quarkus.easynats.log-payloads-on-error` configuration property (default: false).
-   Q: How should duplicate codec registrations (same type) be handled? → A: CDI handles conflict detection. If two codecs are registered for the same type, CDI throws an ambiguity error at startup.

## Clarifications

### Session 2025-11-03

- Q: Should the feature support multiple, type-specific codecs or a single, global codec? → A: Global Codec
- Q: On a deserialization failure, should the message be negatively acknowledged (NACK), causing a redelivery, or should it be terminated (Term) to prevent redelivery? → A: NACK
- Q: What kind of "type validation" should the global codec perform, and how should it be handled? → A: Codec-internal Validation

- Q: How should the `datacontenttype` be determined when a custom codec is active? → A: Let the Codec decide

## Requirements *(mandatory)*

### Functional Requirements

-   **FR-001**: The system MUST provide a public interface for developers to implement a global payload codec.
-   **FR-002**: The system MUST provide a mechanism to provide a single, global custom codec implementation (via a CDI bean).
-   **FR-003**: When a `NatsPublisher` sends a message, it MUST create a CloudEvent envelope. The user's payload object MUST be encoded into a byte array using the global custom codec (if provided), and this byte array MUST be set as the `data` attribute of the CloudEvent. The CloudEvent `datacontenttype` attribute MUST be set to the value returned by the global custom codec's `getContentType()` method.
-   **FR-004**: When a `@NatsSubscriber` receives a message, the system MUST parse the CloudEvent envelope. The `data` attribute of the CloudEvent MUST be decoded using the global custom codec (if provided) to reconstruct the user's payload object.
-   **FR-005**: If no global custom codec is provided, the system MUST default to using Jackson to serialize the payload object into the CloudEvent `data` attribute.
-   **FR-006**: The codec interface MUST support throwing exceptions to signal encoding, decoding, or validation failures.
-   **FR-007**: The system MUST gracefully handle exceptions thrown by the global custom codec during deserialization, preventing the subscriber method from being called and ensuring the message is negatively acknowledged (NACKed). Failures MUST be logged at WARN level.

### Key Entities *(include if feature involves data)*

-   **Codec**: A non-generic interface that developers implement to provide global serialization and deserialization.
    - `byte[] encode(Object object) throws SerializationException`: Encodes an object to bytes. Throws `SerializationException` if encoding fails.
    - `Object decode(byte[] data, Class<?> type) throws DeserializationException`: Decodes bytes to the target type. The `Class<?> type` parameter allows the codec to know the expected target type. Throws `DeserializationException` for validation or decoding failures.
    - `String getContentType()`: Returns the CloudEvents `datacontenttype` for the data produced by this codec.
-   **SerializationException**: A checked exception thrown by `Codec.encode()` when encoding fails.
-   **DeserializationException**: A checked exception thrown by `Codec.decode()` when decoding or validation fails.
-   **DefaultCodec**: An internal CDI bean (`@ApplicationScoped`, `@DefaultBean`) that implements the `Codec` interface and contains the default Jackson/CloudEvents serialization logic. It serves as the fallback if the user does not provide a custom `Codec` bean.

## Success Criteria *(mandatory)*

### Measurable Outcomes

-   **SC-001**: A developer can fully replace the default JSON serialization with a custom format (e.g., a simple string-based format) for a specific message type by implementing one interface and writing a single line of registration code.
-   **SC-002**: The lookup and invocation overhead of the custom codec mechanism MUST add less than 5% latency compared to the baseline default serialization, measured at the 95th percentile.
-   **SC-003**: For any given type, if a custom codec is registered, 100% of messages of that type published via `NatsPublisher` MUST be serialized by that codec.
-   **SC-004**: When a custom codec's `decode` method throws an exception, 100% of those messages MUST be prevented from reaching the `@NatsSubscriber`'s business logic.