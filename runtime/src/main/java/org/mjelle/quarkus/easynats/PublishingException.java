package org.mjelle.quarkus.easynats;

/**
 * Exception thrown when publishing a message to NATS fails.
 *
 * This exception is thrown when:
 * - The payload is null
 * - Serialization of the payload fails
 * - The NATS connection is unavailable
 * - The broker rejects the message
 * - Any other NATS publishing operation fails
 */
public class PublishingException extends Exception {

    /**
     * Constructs a PublishingException with the specified detail message.
     *
     * @param message the detail message
     */
    public PublishingException(String message) {
        super(message);
    }

    /**
     * Constructs a PublishingException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public PublishingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a PublishingException with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public PublishingException(Throwable cause) {
        super(cause);
    }
}
