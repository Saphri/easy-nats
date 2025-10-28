package org.mjelle.quarkus.easynats;

/**
 * Exception thrown when NATS configuration is invalid or missing required properties.
 * This is a domain-specific exception that provides clear error messages for configuration issues.
 */
public class NatsConfigurationException extends RuntimeException {

    /**
     * Creates a new configuration exception with the specified message.
     *
     * @param message the error message describing the configuration problem
     */
    public NatsConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new configuration exception with the specified message and cause.
     *
     * @param message the error message describing the configuration problem
     * @param cause   the underlying cause of the configuration error
     */
    public NatsConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
