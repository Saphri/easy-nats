package org.mjelle.quarkus.easynats.runtime.subscriber;

/**
 * Exception thrown when message deserialization fails.
 *
 * <p>This exception is raised when:
 * - Native type decoding fails (e.g., "abc" cannot be parsed as Integer)
 * - Base64 decoding fails
 * - Space-separated or comma-separated parsing fails
 * - JSON parsing fails
 * - Type conversion fails
 * - Required field is missing from JSON
 * - Setter/constructor not found
 * - Deserialized object is null
 * </p>
 */
public class DeserializationException extends RuntimeException {

    /**
     * Creates a new DeserializationException with the specified message.
     *
     * @param message the detail message
     */
    public DeserializationException(String message) {
        super(message);
    }

    /**
     * Creates a new DeserializationException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public DeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
