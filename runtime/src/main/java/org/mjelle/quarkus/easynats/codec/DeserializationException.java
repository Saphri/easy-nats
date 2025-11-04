package org.mjelle.quarkus.easynats.codec;

/**
 * Checked exception thrown by {@code Codec.decode()} when decoding or validation fails.
 *
 * <p>This exception is caught by the framework. When thrown during deserialization of an incoming
 * message, the framework will:
 *
 * <ol>
 *   <li>Log the error at WARN level
 *   <li>Prevent the subscriber method from being invoked
 *   <li>Negatively acknowledge (NACK) the message
 * </ol>
 */
public class DeserializationException extends Exception {
  /**
   * Constructs a new {@code DeserializationException} with the specified detail message.
   *
   * @param message the detail message. The detail message is saved for later retrieval by the
   *     {@code getMessage()} method.
   */
  public DeserializationException(String message) {
    super(message);
  }

  /**
   * Constructs a new {@code DeserializationException} with the specified detail message and cause.
   *
   * @param message the detail message (which is saved for later retrieval by the {@code
   *     getMessage()} method).
   * @param cause the cause (which is saved for later retrieval by the {@code getCause()} method). A
   *     {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public DeserializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
