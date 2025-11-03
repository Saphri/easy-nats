# Research: Custom Payload Codec

## R-01: Strategy for Handling Generic Types in CodecRegistry

**Decision**: The `CodecRegistry` will only support raw class types for codec registration (e.g., `Product.class`). For generic types like `List<Product>`, the responsibility for handling the inner type's serialization will be delegated to the user's custom `Codec<List>` implementation. The framework will not attempt to automatically discover or compose codecs for parameterized types.

**Rationale**:
1.  **Simplicity and Predictability**: Automatic composition of codecs for generic types can become very complex and lead to unpredictable behavior. For example, should a `Codec<List<Product>>` automatically use a registered `Codec<Product>`? What if the user wants a different binary format for the list itself? A simple, explicit registration model is easier for developers to understand and debug.
2.  **Type Erasure**: Java's type erasure makes it difficult and unreliable to get the concrete generic type parameter at runtime without complex workarounds (like using `TypeLiteral` or similar constructs), which would complicate the DX.
3.  **Flexibility**: Delegating the responsibility to the developer provides maximum flexibility. A developer implementing `Codec<List<Product>>` can choose any serialization strategy for the list and its contents, including calling another registered codec if they wish.

**Alternatives considered**:
-   **TypeLiteral Registration**: Using a `TypeLiteral`-like pattern (`new TypeLiteral<List<Product>>(){}`) for registration. This adds significant complexity to the API for a relatively small benefit.
-   **Recursive Codec Lookup**: The registry could try to find a codec for `List` and a codec for `Product` and combine them. This is complex, error-prone, and makes assumptions about how the user wants to serialize the data.

## R-02: Best Practices for `@DefaultBean` in Quarkus

**Decision**: A `DefaultCodec` class implementing `Codec<Object>` will be created and annotated with `@ApplicationScoped` and `@DefaultBean`. This bean will encapsulate the existing Jackson/CloudEvents serialization logic.

**Rationale**:
-   `@DefaultBean` is the idiomatic Quarkus way to provide a default implementation of an interface that can be easily replaced by a user-provided bean.
-   By making it a `Codec<Object>`, it can serve as a catch-all for any type that does not have a specific, custom codec registered.
-   The user's custom codec beans will be discovered by CDI and will take precedence over the `@DefaultBean` without any additional configuration.

**Alternatives considered**:
-   **Programmatic Fallback**: Having the `CodecRegistry` programmatically instantiate and use the default codec if no custom one is found. This is less idiomatic and couples the registry to the default implementation. Using `@DefaultBean` leverages the power of CDI for a cleaner separation of concerns.
