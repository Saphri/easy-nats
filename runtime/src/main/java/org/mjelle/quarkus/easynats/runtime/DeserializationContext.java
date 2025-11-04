package org.mjelle.quarkus.easynats.runtime;

import org.mjelle.quarkus.easynats.runtime.subscriber.MessageType;

/**
 * Metadata and state for deserializing a message to a typed object.
 *
 * <p>Encapsulates information needed to deserialize a message, including the validated type
 * definition, raw payload bytes, and timing for error logging.
 */
public class DeserializationContext<T> {
  private final MessageType<T> messageType;
  private final byte[] rawPayload;
  private final long deserializationStartTime;

  /**
   * Creates a deserialization context.
   *
   * @param messageType the validated type definition
   * @param rawPayload the raw JSON bytes from NATS message
   */
  public DeserializationContext(MessageType<T> messageType, byte[] rawPayload) {
    this.messageType = messageType;
    this.rawPayload = rawPayload;
    this.deserializationStartTime = System.currentTimeMillis();

    // Validate invariants
    if (!messageType.getValidationResult().isValid()) {
      throw new IllegalArgumentException("MessageType must be valid for deserialization");
    }
    if (rawPayload == null) {
      throw new IllegalArgumentException("Raw payload cannot be null");
    }
  }

  /** Returns the validated type definition. */
  public MessageType<T> getMessageType() {
    return messageType;
  }

  /** Returns the raw JSON bytes from NATS message. */
  public byte[] getRawPayload() {
    return rawPayload;
  }

  /** Returns the raw payload as a UTF-8 string (limited to first 1000 characters for logging). */
  public String getRawPayloadAsString() {
    try {
      String payload = new String(rawPayload, "UTF-8");
      if (payload.length() > 1000) {
        return payload.substring(0, 1000) + "... [truncated]";
      }
      return payload;
    } catch (Exception e) {
      return "[Unable to decode payload as UTF-8]";
    }
  }

  /** Returns the time when deserialization started (milliseconds since epoch). */
  public long getDeserializationStartTime() {
    return deserializationStartTime;
  }

  /** Returns the elapsed time since deserialization started (in milliseconds). */
  public long getElapsedTimeMs() {
    return System.currentTimeMillis() - deserializationStartTime;
  }

  @Override
  public String toString() {
    return String.format(
        "DeserializationContext{type=%s, payloadSize=%d bytes, elapsed=%dms}",
        messageType.getTypeName(), rawPayload.length, getElapsedTimeMs());
  }
}
