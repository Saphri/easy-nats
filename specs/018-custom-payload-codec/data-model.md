# Data Model: Custom Payload Codec

This document defines the key data structures for the global Custom Payload Codec feature.

## 1. `Codec` Interface

This is the public interface that developers will implement to provide a single, global serialization and deserialization strategy for the CloudEvent `data` payload.

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Signature**:
    ```java
    public interface Codec {
        /**
         * Returns the content type of the data produced by this codec.
         * This value will be used to set the 'datacontenttype' attribute of the CloudEvent.
         * @return The content type string (e.g., "application/protobuf", "text/plain").
         */
        String getContentType();

        /**
         * Encodes the given object into a byte array.
         * @param object The object to encode.
         * @return The byte array representation of the object.
         * @throws SerializationException if encoding fails.
         */
        byte[] encode(Object object) throws SerializationException;

        /**
         * Decodes a byte array into an object of the specified type.
         * @param data The byte array from the CloudEvent 'data' attribute.
         * @param type The target class of the object, determined by the subscriber's method signature.
         * @param ceType The 'type' attribute from the CloudEvent headers.
         * @return The decoded object.
         * @throws DeserializationException if decoding or validation fails.
         */
        Object decode(byte[] data, Class<?> type, String ceType) throws DeserializationException;
    }
    ```
-   **Implementation Note**: Implementations of this interface **MUST be thread-safe**, as they will be registered as `@ApplicationScoped` CDI beans and used concurrently.

## 2. Exceptions

### `SerializationException`

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Type**: `public class SerializationException extends Exception`
-   **Purpose**: Thrown by the `encode` method when an object cannot be serialized. This is a checked exception that will be propagated to the `NatsPublisher.publish()` call.

### `DeserializationException`

-   **Package**: `org.mjelle.quarkus.easynats.codec`
-   **Type**: `public class DeserializationException extends Exception`
-   **Purpose**: Thrown by the `decode` method when a byte array cannot be deserialized. This exception will be caught by the framework, logged, and will result in the message being NACKed.

## 3. Internal Components

### `DefaultCodec`

-   **Type**: Internal CDI Bean (`@ApplicationScoped`, `@DefaultBean`)
-   **Purpose**: Implements the `Codec` interface and contains the default Jackson serialization logic for the CloudEvent `data` payload. Its `getContentType()` will return `application/json`.
