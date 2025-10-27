package org.mjelle.quarkus.easynats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Encodes typed payloads to JSON bytes using Jackson.
 *
 * Supports only Jackson-compatible types (POJOs, records, generic types with Jackson annotations).
 * Primitives, arrays, and other unsupported types must be wrapped in Jackson-compatible POJOs
 * by the user before calling this encoder.
 *
 * This simplified encoder eliminates "magic" by enforcing a single, explicit rule:
 * If Jackson can serialize it, we support it; if not, users wrap the type.
 */
public class TypedPayloadEncoder {

    private TypedPayloadEncoder() {
        // Utility class; no instances
    }

    /**
     * Encodes a typed object to JSON bytes using Jackson.
     *
     * @param value the object to serialize (must be Jackson-compatible)
     * @param mapper the Jackson ObjectMapper
     * @return JSON-encoded byte array
     * @throws SerializationException if serialization fails
     *
     * @throws IllegalArgumentException if value is null
     */
    public static byte[] encodeWithJackson(Object value, ObjectMapper mapper) throws SerializationException {
        if (value == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }

        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            String className = value.getClass().getSimpleName();
            throw new SerializationException(
                "Failed to serialize " + className + ": " + e.getMessage(), e
            );
        }
    }
}
