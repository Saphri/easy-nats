package org.mjelle.quarkus.easynats.codec;

/**
 * Public interface for custom payload serialization and deserialization.
 *
 * <p>Implementations of this interface provide a way to customize the serialization of the
 * CloudEvent {@code data} attribute. When a developer provides a custom {@code Codec} bean
 * (registered as an {@code @ApplicationScoped} CDI bean), the EasyNATS extension will use it for
 * all publish and subscribe operations, bypassing the default Jackson-based serialization.
 *
 * <p><strong>Thread Safety:</strong> Implementations of this interface MUST be thread-safe. The
 * codec will be registered as an {@code @ApplicationScoped} singleton and invoked concurrently by
 * multiple publishers and subscribers. Developers are responsible for ensuring all state maintained
 * by the codec (e.g., JAXB contexts, thread-safe caches) is safe for concurrent access.
 */
public interface Codec {
  /**
   * Returns the content type of the data produced by this codec.
   *
   * <p>This value will be used to set the {@code datacontenttype} attribute of the CloudEvent.
   *
   * @return The content type string (e.g., {@code "application/protobuf"}, {@code "text/plain"}).
   *     Must not be {@code null}.
   */
  String getContentType();

  /**
   * Encodes the given object into a byte array.
   *
   * <p>This method is called by {@code NatsPublisher} when publishing a message. The returned byte
   * array will be set as the {@code data} attribute of the CloudEvent envelope.
   *
   * @param object The object to encode. May be any type supported by the codec implementation.
   * @return The byte array representation of the object. Must not be {@code null} or empty.
   * @throws SerializationException if encoding fails. This exception will be propagated to the
   *     caller of {@code NatsPublisher.publish()}.
   */
  byte[] encode(Object object) throws SerializationException;

  /**
   * Decodes a byte array into an object of the specified type.
   *
   * <p>This method is called by the subscriber handler when receiving a message. The codec should
   * use the {@code type} parameter to understand the expected target type and the {@code ceType}
   * parameter (from CloudEvent headers) for additional context if needed.
   *
   * @param data The byte array from the CloudEvent {@code data} attribute. Will not be {@code
   *     null}.
   * @param type The target class of the object, determined by the subscriber's method signature.
   *     Will not be {@code null}.
   * @param ceType The {@code ce-type} attribute from the CloudEvent headers. May be {@code null}.
   * @return The decoded object. Must be assignable to the {@code type} parameter to avoid {@code
   *     ClassCastException} in the framework.
   * @throws DeserializationException if decoding or validation fails. This exception will be caught
   *     by the framework, logged, and the message will be NACKed.
   */
  Object decode(byte[] data, Class<?> type, String ceType) throws DeserializationException;
}
