# Implementation Plan: Custom Payload Codec (Global)

**Feature Branch**: `018-custom-payload-codec`
**Status**: In Progress

## Phase 0: Technical Context & Constitution Check

### 1. Technical Context

-   **High-Level Approach**: The feature will provide a mechanism for developers to replace the default Jackson/CloudEvents serialization with a single, global custom codec. This is achieved by defining a `Codec` interface. Developers can provide one implementation of this interface as a CDI bean. If such a bean is present in the application, the EasyNATS extension will use it for all serialization and deserialization. Otherwise, it will use a default implementation that preserves the existing CloudEvents behavior.

-   **Key Components**:
    -   `Codec`: A public interface for developers to implement. It will define methods to handle any object type: `byte[] encode(Object object)` and `Object decode(byte[] data, Class<?> type)`.
    -   `DefaultCodec`: A Quarkus `@DefaultBean` implementation of the `Codec` interface. This bean will contain the current Jackson/CloudEvents logic, ensuring full backward compatibility if no custom codec is provided.
    -   `NatsPublisher`: Will be modified to receive an injection of the global `Codec` and use it for all serialization.
    -   Subscriber Invocation Logic: The build-time logic will be adjusted to ensure the global `Codec` is used for deserializing all incoming messages before they are passed to `@NatsSubscriber` methods.

-   **Dependencies**:
    -   Quarkus CDI (`quarkus-arc`): Used for discovering the user-provided codec bean and for the `@DefaultBean` mechanism.
    -   No new external dependencies are required.

-   **User Input Analysis**:
    -   "the codec is global.. not per type": This is the core directive. The design is now centered around a single, application-wide codec rather than a registry of type-specific codecs. This simplifies the implementation significantly.

-   **Unknowns & Risks**:
    -   **Risk**: A global codec shifts more responsibility to the developer. They must handle different object types within a single `encode`/`decode` implementation (e.g., using `instanceof` checks or a serialization library that handles polymorphism). The documentation must be very clear about this.
    -   **Risk**: The `decode` method returns `Object`. The subscriber invocation logic must safely cast this to the type expected by the `@NatsSubscriber` method. This cast is a potential source of `ClassCastException` if the codec returns the wrong type, so error handling must be robust.

### 2. Constitution Check

| Principle | Adherence | Justification |
| :--- | :--- | :--- |
| **I. Extension-First Architecture** | ✅ Yes | The `Codec` interface will be in the `runtime` module, and the logic for injecting and using it will be correctly placed in the `runtime` and `deployment` modules. |
| **II. Minimal Runtime Dependencies** | ✅ Yes | No new runtime dependencies are being added. |
| **III. Test-Driven Development** | ✅ Yes | An integration test will be written to verify that a user-provided global codec is correctly discovered and used for both publishing and subscribing. |
| **IV. Java 21 Compatibility** | ✅ Yes | All new code will be Java 21 compliant. |
| **V. CloudEvents Compliance** | ✅ Yes | The default codec will ensure CloudEvents compliance is maintained. The user is explicitly given the power to override this, which is the feature's purpose. |
| **VI. Developer Experience First** | ✅ Yes | This approach simplifies the API. Instead of registering multiple codecs, the developer provides a single bean to customize serialization, which is a common pattern in Quarkus. |
| **VII. Observability First** | ✅ Yes | This change does not impact tracing or health checks. |

**Gate Evaluation**: The plan adheres to all core principles and development quality gates.

---

## Phase 1: Research & Design

### 1. Research (`research.md`)

-   No research is required for this simplified design. The use of a global CDI bean with a `@DefaultBean` fallback is a standard and well-understood pattern in Quarkus.

### 2. Data Model (`data-model.md`)

-   **`Codec` Interface**:
    -   `byte[] encode(Object object) throws SerializationException;`
    -   `Object decode(byte[] data, Class<?> type) throws DeserializationException;`
-   **`SerializationException`**: Custom exception for encoding failures.
-   **`DeserializationException`**: Custom exception for decoding failures.

### 3. API Contracts & Quickstart

-   **API Contracts**: The public contract is the `Codec` interface.
-   **Quickstart Guide (`quickstart.md`)**:
    -   Show how to create a global `JsonbCodec` that uses `jakarta.json.bind.Jsonb` for all serialization.
    -   Demonstrate how to declare it as an `@ApplicationScoped` bean.
    -   Provide examples of publishing and subscribing with different data types, showing that the single global codec is used for all of them.

---

## Phase 2: Implementation Plan

*(This section will be detailed after Phase 1 is complete)*