package org.mjelle.quarkus.easynats;

/**
 * Checked exception for serialization/deserialization failures.
 *
 * Wraps Jackson JsonProcessingException and other serialization errors
 * with user-friendly messages for debugging and error handling.
 */
public class SerializationException extends Exception {

    /**
     * Constructs a SerializationException with the specified message.
     *
     * @param message the error message describing the serialization failure
     */
    public SerializationException(String message) {
        super(message);
    }

    /**
     * Constructs a SerializationException with the specified message and cause.
     *
     * @param message the error message describing the serialization failure
     * @param cause the underlying exception (e.g., JsonProcessingException)
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
