# Data Model: Custom Payload Codec

This document defines the key data structures and interfaces for the Custom Payload Codec feature.

## 1. `Codec<T>` Interface

This is the central public interface that developers will implement to provide custom serialization and deserialization logic.

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Signature**:
    ```java
    public interface Codec<T> {
        /**
         * Encodes the given object into a byte array.
         * @param object The object to encode.
         * @return The byte array representation of the object.
         * @throws SerializationException if encoding fails.
         */
        byte[] encode(T object) throws SerializationException;

        /**
         * Decodes a byte array into an object of the specified type.
         * @param data The byte array to decode.
         * @param type The target class of the object.
         * @return The decoded object.
         * @throws DeserializationException if decoding or validation fails.
         */
        T decode(byte[] data, Class<T> type) throws DeserializationException;
    }
    ```

## 2. Exceptions

These custom exceptions provide clear error handling for codec operations.

### `SerializationException`

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Type**: `public class SerializationException extends Exception`
-   **Purpose**: Thrown by the `encode` method when an object cannot be serialized.

### `DeserializationException`

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Type**: `public class DeserializationException extends Exception`
-   **Purpose**: Thrown by the `decode` method when a byte array cannot be deserialized or fails validation.

## 3. Internal Components

These components are not part of the public API but are crucial for the implementation.

### `CodecRegistry`

-   **Type**: Internal CDI Bean (`@ApplicationScoped`)
-   **Purpose**: Manages the lifecycle and lookup of all `Codec` implementations.
-   **Key Methods**:
    -   `void register(Class<?> type, Codec<?> codec)`: Registers a codec for a specific type.
    -   `Optional<Codec<?>> getCodec(Class<?> type)`: Retrieves the codec for a given type, returning the custom one if it exists, otherwise falling back to the default.

### `DefaultCodec`

-   **Type**: Internal CDI Bean (`@ApplicationScoped`, `@DefaultBean`)
-   **Purpose**: Implements `Codec<Object>` and contains the default Jackson/CloudEvents serialization logic. It serves as the fallback for any type without a custom codec.
