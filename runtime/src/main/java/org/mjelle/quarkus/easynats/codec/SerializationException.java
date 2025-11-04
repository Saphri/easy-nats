package org.mjelle.quarkus.easynats.codec;

/**
 * Checked exception thrown by {@code Codec.encode()} when encoding fails.
 *
 * <p>This exception is propagated to the caller of {@code NatsPublisher.publish()}, allowing the
 * developer to handle encoding failures in their publish logic.
 */
public class SerializationException extends Exception {
  /**
   * Constructs a new {@code SerializationException} with the specified detail message.
   *
   * @param message the detail message. The detail message is saved for later retrieval by the
   *     {@code getMessage()} method.
   */
  public SerializationException(String message) {
    super(message);
  }

  /**
   * Constructs a new {@code SerializationException} with the specified detail message and cause.
   *
   * @param message the detail message (which is saved for later retrieval by the {@code
   *     getMessage()} method).
   * @param cause the cause (which is saved for later retrieval by the {@code getCause()} method). A
   *     {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public SerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
