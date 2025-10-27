package org.mjelle.quarkus.easynats.runtime.subscriber;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mjelle.quarkus.easynats.runtime.DeserializationContext;
import java.io.IOException;

/**
 * Deserializes CloudEvent data into typed objects using Jackson only.
 *
 * Supports only Jackson-compatible types (POJOs, records, generic types with Jackson annotations).
 * Primitives, arrays, and other unsupported types must be wrapped in Jackson-compatible POJOs
 * by the user before calling this deserializer.
 *
 * This simplified deserializer eliminates "magic" by enforcing a single, explicit rule:
 * If Jackson can deserialize it, we support it; if not, users wrap the type.
 */
public final class MessageDeserializer {

    private MessageDeserializer() {
        // Utility class; no instances
    }

    /**
     * Deserialize CloudEvent data into typed object using Jackson.
     *
     * @param data the raw JSON bytes from NATS message
     * @param targetType the Java type to deserialize into
     * @param objectMapper Jackson ObjectMapper to use
     * @return deserialized object of type T
     * @throws DeserializationException if data cannot be deserialized
     */
    public static <T> T deserialize(byte[] data, Class<T> targetType, ObjectMapper objectMapper)
            throws DeserializationException {
        if (data == null || data.length == 0) {
            throw new DeserializationException("Data cannot be null or empty");
        }
        if (targetType == null) {
            throw new DeserializationException("Target type cannot be null");
        }
        if (objectMapper == null) {
            throw new DeserializationException("ObjectMapper cannot be null");
        }

        try {
            T result = objectMapper.readValue(data, targetType);
            if (result == null) {
                throw new DeserializationException(
                    "Deserialization resulted in null for type " + targetType.getName()
                );
            }
            return result;
        } catch (IOException e) {
            throw new DeserializationException(
                "Failed to deserialize to type " + targetType.getName() + ": " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Deserialize CloudEvent data using a generic type (via JavaType).
     *
     * @param data the raw JSON bytes from NATS message
     * @param jacksonType Jackson's JavaType for the target type (supports generics)
     * @param objectMapper Jackson ObjectMapper to use
     * @return deserialized object of type T
     * @throws DeserializationException if data cannot be deserialized
     */
    public static <T> T deserialize(byte[] data, JavaType jacksonType, ObjectMapper objectMapper)
            throws DeserializationException {
        if (data == null || data.length == 0) {
            throw new DeserializationException("Data cannot be null or empty");
        }
        if (jacksonType == null) {
            throw new DeserializationException("JavaType cannot be null");
        }
        if (objectMapper == null) {
            throw new DeserializationException("ObjectMapper cannot be null");
        }

        try {
            T result = objectMapper.readValue(data, jacksonType);
            if (result == null) {
                throw new DeserializationException(
                    "Deserialization resulted in null for type " + jacksonType.getTypeName()
                );
            }
            return result;
        } catch (IOException e) {
            throw new DeserializationException(
                "Failed to deserialize to type " + jacksonType.getTypeName() + ": " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Deserialize CloudEvent data with full deserialization context (for error logging).
     *
     * @param context the deserialization context with message type and raw payload
     * @return deserialized object of the context's target type
     * @throws DeserializationException if data cannot be deserialized
     */
    public static <T> T deserialize(DeserializationContext<T> context) throws DeserializationException {
        if (context == null) {
            throw new DeserializationException("DeserializationContext cannot be null");
        }

        try {
            T result = context.getMessageType().getObjectMapper()
                .readValue(context.getRawPayload(), context.getMessageType().getJacksonType());
            if (result == null) {
                throw new DeserializationException(
                    "Deserialization resulted in null for type " + context.getMessageType().getTypeName()
                );
            }
            return result;
        } catch (IOException e) {
            String errorMessage = String.format(
                "Failed to deserialize to type '%s': %s\n" +
                "Raw payload: %s",
                context.getMessageType().getTypeName(),
                e.getMessage(),
                context.getRawPayloadAsString()
            );
            throw new DeserializationException(errorMessage, e);
        }
    }
}
