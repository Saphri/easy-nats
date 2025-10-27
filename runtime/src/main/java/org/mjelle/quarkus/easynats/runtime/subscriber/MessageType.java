package org.mjelle.quarkus.easynats.runtime.subscriber;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Encapsulates a user-provided type with Jackson validation metadata.
 *
 * Holds the raw Java class, Jackson's internal type representation,
 * validation results, and the ObjectMapper instance for serialization/deserialization.
 *
 * @param <T> the user-provided type (POJO, record, etc.)
 */
public class MessageType<T> {
    private final Class<T> rawClass;
    private final JavaType jacksonType;
    private final TypeValidationResult validationResult;
    private final ObjectMapper objectMapper;

    /**
     * Creates a MessageType with validation.
     *
     * @param rawClass the Java class (POJO, record, etc.)
     * @param jacksonType Jackson's internal type representation
     * @param validationResult result of type validation
     * @param objectMapper the ObjectMapper for serialization/deserialization
     */
    public MessageType(
        Class<T> rawClass,
        JavaType jacksonType,
        TypeValidationResult validationResult,
        ObjectMapper objectMapper
    ) {
        this.rawClass = rawClass;
        this.jacksonType = jacksonType;
        this.validationResult = validationResult;
        this.objectMapper = objectMapper;

        // Validate invariants
        if (rawClass == null) {
            throw new IllegalArgumentException("rawClass cannot be null");
        }
        if (jacksonType == null) {
            throw new IllegalArgumentException("jacksonType cannot be null");
        }
        if (validationResult == null) {
            throw new IllegalArgumentException("validationResult cannot be null");
        }
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(
                "MessageType requires valid type: " + validationResult.getErrorMessage()
            );
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper cannot be null");
        }
    }

    /**
     * Returns the raw Java class.
     */
    public Class<T> getRawClass() {
        return rawClass;
    }

    /**
     * Returns Jackson's internal type representation.
     */
    public JavaType getJacksonType() {
        return jacksonType;
    }

    /**
     * Returns the type validation result.
     */
    public TypeValidationResult getValidationResult() {
        return validationResult;
    }

    /**
     * Returns the ObjectMapper instance for serialization/deserialization.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Returns the fully qualified type name.
     */
    public String getTypeName() {
        return rawClass.getName();
    }

    /**
     * Returns the simple type name (without package).
     */
    public String getSimpleTypeName() {
        return rawClass.getSimpleName();
    }

    /**
     * Checks if this type is valid.
     */
    public boolean isValid() {
        return validationResult.isValid();
    }

    @Override
    public String toString() {
        return String.format(
            "MessageType<%s>{valid=%s, jacksonType=%s}",
            rawClass.getSimpleName(),
            validationResult.isValid(),
            jacksonType.getTypeName()
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MessageType)) return false;
        MessageType<?> other = (MessageType<?>) obj;
        return rawClass.equals(other.rawClass);
    }

    @Override
    public int hashCode() {
        return rawClass.hashCode();
    }
}
