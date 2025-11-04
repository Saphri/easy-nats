package org.mjelle.quarkus.easynats.runtime;

import org.mjelle.quarkus.easynats.runtime.subscriber.MessageType;

/**
 * Metadata and state for serializing a typed message.
 *
 * <p>Encapsulates information needed to serialize a message, including the validated type
 * definition and the payload to be serialized.
 */
public class SerializationContext<T> {
  private final MessageType<T> messageType;
  private final T payload;
  private final long serializationStartTime;

  /**
   * Creates a serialization context.
   *
   * @param messageType the validated type definition
   * @param payload the object to serialize
   */
  public SerializationContext(MessageType<T> messageType, T payload) {
    this.messageType = messageType;
    this.payload = payload;
    this.serializationStartTime = System.currentTimeMillis();

    // Validate invariants
    if (!messageType.getValidationResult().isValid()) {
      throw new IllegalArgumentException("MessageType must be valid for serialization");
    }
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null");
    }
  }

  /** Returns the validated type definition. */
  public MessageType<T> getMessageType() {
    return messageType;
  }

  /** Returns the payload to be serialized. */
  public T getPayload() {
    return payload;
  }

  /** Returns the time when serialization started (milliseconds since epoch). */
  public long getSerializationStartTime() {
    return serializationStartTime;
  }

  /** Returns the elapsed time since serialization started (in milliseconds). */
  public long getElapsedTimeMs() {
    return System.currentTimeMillis() - serializationStartTime;
  }

  @Override
  public String toString() {
    return String.format(
        "SerializationContext{type=%s, payloadClass=%s, elapsed=%dms}",
        messageType.getTypeName(), payload.getClass().getSimpleName(), getElapsedTimeMs());
  }
}
