# Implementation Plan: Custom Payload Codec

**Feature Branch**: `018-custom-payload-codec`
**Status**: In Progress

## Phase 0: Technical Context & Constitution Check

### 1. Technical Context

-   **High-Level Approach**: The goal is to introduce a mechanism that allows developers to override the default Jackson/CloudEvents serialization. This will be achieved by defining a `Codec` interface and a `CodecRegistry` to manage custom implementations. The system will look up a codec for a given type; if a custom one is found, it will be used for both serialization (in `NatsPublisher`) and deserialization (in the subscriber logic). If not, it will fall back to the default.

-   **Key Components**:
    -   `Codec<T>`: A public interface for developers to implement. It will define `byte[] encode(T object)` and `T decode(byte[] data)` methods.
    -   `CodecRegistry`: An internal CDI bean that will hold a map of `Class<?>` to `Codec<?>`. It will be responsible for registering and looking up codecs.
    -   `DefaultCodec`: A Quarkus `@DefaultBean` implementation of the `Codec` interface that encapsulates the current Jackson/CloudEvents logic. This ensures backward compatibility.
    -   `NatsPublisher`: Will be modified to consult the `CodecRegistry` before sending a message.
    -   Subscriber Invocation Logic: The part of the `deployment` module that handles message dispatching to `@NatsSubscriber` methods will be updated to use the `CodecRegistry` for deserialization.

-   **Dependencies**:
    -   Quarkus CDI (`quarkus-arc`): For bean management, including `@ApplicationScoped`, `@DefaultBean`, and programmatic lookup.
    -   No new external dependencies are anticipated.

-   **User Input Analysis**:
    -   "both encode and decode must validate the type is allowed (cached)": This implies the `CodecRegistry` should cache lookups for performance. The "allowed" part suggests that the type must be explicitly registered.
    -   "Quarkus @DefaultBean can provide the default codec": This is a direct implementation hint. We will create a `DefaultCodec` and mark it with `@DefaultBean`. Custom codecs provided by the user will override it.
    -   "@SerializationException and @DeserializationException be used from then codec": We will need to define these two custom, checked exceptions to provide clear error handling.

-   **Unknowns & Risks**:
    -   **Risk**: Modifying the core publisher and subscriber logic is sensitive. Extensive integration testing is required to ensure we don't break existing functionality. The user noted that existing tests should cover this, which is a good starting point.
    -   **Unknown**: How to handle generic types and type erasure in the `CodecRegistry`. We need a reliable way to map a `Class<?>` to its codec, especially for collections or generic container types. [NEEDS CLARIFICATION: What is the strategy for handling generic types like `List<Product>`? Do users register a codec for `List` or for `Product`?]

### 2. Constitution Check

| Principle | Adherence | Justification |
| :--- | :--- | :--- |
| **I. Extension-First Architecture** | ✅ Yes | The changes will be correctly split between `runtime` (new `Codec` interface, exceptions) and `deployment` (modifications to the build-time subscriber processing). |
| **II. Minimal Runtime Dependencies** | ✅ Yes | No new runtime dependencies will be added. The feature extends existing functionality. |
| **III. Test-Driven Development** | ✅ Yes | New integration tests will be created to validate the custom codec functionality before implementation. |
| **IV. Java 21 Compatibility** | ✅ Yes | All new code will be Java 21 compliant. |
| **V. CloudEvents Compliance** | ✅ Yes | The default behavior remains CloudEvents compliant. This feature allows *overriding* it, which is the explicit goal. |
| **VI. Developer Experience First** | ✅ Yes | The feature enhances DX by giving developers more control. The registration mechanism will be designed to be simple and intuitive. |
| **VII. Observability First** | ✅ Yes | No changes are planned for tracing or health checks, so existing observability will not be affected. |

**Gate Evaluation**: All development quality gates from the constitution will be followed. The plan adheres to all core principles.

---

## Phase 1: Research & Design

### 1. Research (`research.md`)

-   **R-01**: Investigate and decide on a strategy for handling generic types in the `CodecRegistry`. This involves exploring Quarkus's `Type` discovery mechanisms and how to robustly map parameterized types like `List<Product>` or `Map<String, User>` to a specific codec.
-   **R-02**: Research best practices for creating and using `@DefaultBean` in Quarkus to ensure the fallback mechanism is implemented correctly and robustly.

### 2. Data Model (`data-model.md`)

-   **`Codec<T>` Interface**:
    -   `byte[] encode(T object) throws SerializationException;`
    -   `T decode(byte[] data, Class<T> type) throws DeserializationException;`
-   **`SerializationException`**: Custom exception for encoding failures.
-   **`DeserializationException`**: Custom exception for decoding failures, including validation errors.
-   **`CodecRegistry`**:
    -   `void register(Class<?> type, Codec<?> codec);`
    -   `Optional<Codec<?>> getCodec(Class<?> type);`

### 3. API Contracts & Quickstart

-   **API Contracts**: No external API contracts (e.g., REST) are part of this feature. The public contract is the `Codec<T>` interface and the registration mechanism.
-   **Quickstart Guide (`quickstart.md`)**:
    -   Show how to create a custom `StringCodec`.
    -   Demonstrate how to register the codec in a Quarkus application (e.g., using a startup bean).
    -   Provide an example of injecting `NatsPublisher<String>` and using a `@NatsSubscriber` with a `String` parameter, showing that the custom codec is invoked.

---

## Phase 2: Implementation Plan

*(This section will be detailed after Phase 1 is complete)*