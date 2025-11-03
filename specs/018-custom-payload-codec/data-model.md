# Data Model: Custom Payload Codec (Global)

This document defines the key data structures for the global Custom Payload Codec feature.

## 1. `Codec` Interface

This is the public interface that developers will implement to provide a single, global serialization and deserialization strategy.

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Signature**:
    ```java
    public interface Codec {
        /**
         * Encodes the given object into a byte array.
         * @param object The object to encode. Can be of any type.
         * @return The byte array representation of the object.
         * @throws SerializationException if encoding fails.
         */
        byte[] encode(Object object) throws SerializationException;

        /**
         * Decodes a byte array into an object of the specified type.
         * @param data The byte array to decode.
         * @param type The target class of the object, provided by the subscriber's method signature.
         * @return The decoded object. The object's class should be compatible with the 'type' parameter.
         * @throws DeserializationException if decoding or validation fails.
         */
        Object decode(byte[] data, Class<?> type) throws DeserializationException;
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

### `DefaultCodec`

-   **Type**: Internal CDI Bean (`@ApplicationScoped`, `@DefaultBean`)
-   **Purpose**: Implements the `Codec` interface and contains the default Jackson/CloudEvents serialization logic. It serves as the fallback if the user does not provide a custom `Codec` bean.