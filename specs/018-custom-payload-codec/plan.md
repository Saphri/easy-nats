# Implementation Plan: Custom Payload Codec

**Feature Branch**: `018-custom-payload-codec`
**Status**: In Progress

## Phase 0: Technical Context & Constitution Check

### 1. Technical Context

-   **High-Level Approach**: The feature will introduce a global mechanism for developers to customize the serialization of the `data` attribute within the CloudEvent envelope. This is achieved by defining a `Codec` interface. Developers can provide a single CDI bean implementing this interface. The EasyNATS extension will discover this bean and use it for encoding and decoding the CloudEvent payload for all publishers and subscribers. If no custom bean is provided, a default implementation using Jackson will be used, ensuring backward compatibility.

-   **Key Components**:
    -   `Codec`: A public interface in the `runtime` module. It will define methods for encoding (`Object -> byte[]`), decoding (`byte[] -> Object`), and specifying the content type.
    -   `DefaultCodec`: An internal CDI bean in the `runtime` module, annotated with `@DefaultBean`, that implements the `Codec` interface using the existing Jackson-based serialization for the CloudEvent `data` payload.
    -   `NatsPublisher`: This existing class in the `runtime` module will be modified to inject the global `Codec`. When publishing, it will use the codec to serialize the user's object before wrapping it in a CloudEvent.
    -   `EasyNatsProcessor`: This existing class in the `deployment` module will be modified. The logic that generates the subscriber message handling code will be updated to invoke the `Codec` for deserialization of the CloudEvent `data` attribute.

-   **Dependencies**:
    -   Quarkus CDI (`quarkus-arc`): For discovering the user-provided codec bean and for the `@DefaultBean` mechanism.
    -   No new external dependencies are required.

-   **Unknowns & Risks**:
    -   **Risk**: The `decode` method returns a raw `Object`. The generated subscriber invocation code must perform a safe cast to the type expected by the user's `@NatsSubscriber` method. While the `Class<?> type` parameter is passed to the codec, a misbehaving codec could still return an incompatible type, leading to a `ClassCastException`. The generated code must handle this gracefully.
    -   **Risk**: The user's `Codec` implementation must be thread-safe, as it will be a singleton (`@ApplicationScoped`) bean used by concurrent publishers and subscribers. This must be clearly documented.

### 2. Constitution Check

| Principle | Adherence | Justification |
| :--- | :--- | :--- |
| **I. Extension-First Architecture** | ✅ Yes | The `Codec` interface and `DefaultCodec` will reside in the `runtime` module. The build-time logic for subscriber generation will be modified in the `deployment` module. |
| **II. Minimal Runtime Dependencies** | ✅ Yes | No new runtime dependencies will be added. |
| **III. Test-Driven Development** | ✅ Yes | An integration test will be created first to validate that a custom global codec is correctly used for both publishing and subscribing, including content-type header verification. |
| **IV. Java 21 Compatibility** | ✅ Yes | All new code will be Java 21 compliant. |
| **V. CloudEvents Compliance** | ✅ Yes | The feature explicitly works *within* the CloudEvents structure, customizing only the `data` payload and `datacontenttype` header, thus preserving CloudEvents compliance. |
| **VI. Developer Experience First** | ✅ Yes | The use of a single, optional CDI bean to override behavior is a simple and idiomatic pattern in Quarkus, providing high control with low configuration overhead. |
| **VII. Observability First** | ✅ Yes | This change does not impact tracing or health checks. |

**Gate Evaluation**: The plan adheres to all core principles and development quality gates.

---

## Phase 1: Research & Design

### 1. Research (`research.md`)

-   No research is required. The design is now fully clarified and relies on standard Quarkus CDI patterns.

### 2. Data Model (`data-model.md`)

-   **`Codec` Interface**:
    -   `String getContentType();`
    -   `byte[] encode(Object object) throws SerializationException;`
    -   `Object decode(byte[] data, Class<?> type, String ceType) throws DeserializationException;`
-   **`SerializationException`**: Custom checked exception for encoding failures.
-   **`DeserializationException`**: Custom checked exception for decoding failures.

### 3. API Contracts & Quickstart

-   **API Contracts**: The public contract is the `Codec` interface.
-   **Quickstart Guide (`quickstart.md`)**:
    -   Show how to create a global `ProtobufCodec`.
    -   Demonstrate how to declare it as an `@ApplicationScoped` bean.
    -   Provide an example of publishing and subscribing with a Protobuf-generated class, showing that the custom codec is used and the `datacontenttype` is set to `application/protobuf`.

---

## Phase 2: Implementation Plan

*(This section will be detailed after Phase 1 is complete)*