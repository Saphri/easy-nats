package org.mjelle.quarkus.easynats.runtime.subscriber;

/**
 * Exception thrown when CloudEvent validation fails.
 *
 * <p>This exception is raised when: - Required CloudEvents 1.0 headers (ce-specversion, ce-type,
 * ce-source, ce-id) are missing - The ce-specversion is not "1.0" - The message does not follow
 * CloudEvents binary-mode protocol - Structured-mode CloudEvents are detected (not supported)
 */
public class CloudEventException extends RuntimeException {

  /**
   * Creates a new CloudEventException with the specified message.
   *
   * @param message the detail message
   */
  public CloudEventException(String message) {
    super(message);
  }

  /**
   * Creates a new CloudEventException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public CloudEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
