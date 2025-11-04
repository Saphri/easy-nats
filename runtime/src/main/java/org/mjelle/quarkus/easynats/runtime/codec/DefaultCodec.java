package org.mjelle.quarkus.easynats.runtime.codec;

import jakarta.enterprise.context.ApplicationScoped;

import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import org.mjelle.quarkus.easynats.codec.SerializationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.DefaultBean;

/**
 * Default codec implementation that uses Jackson for serialization and deserialization.
 *
 * <p>This is an internal bean that is automatically discovered and registered by the EasyNATS
 * extension. It serves as the fallback codec if the user does not provide a custom {@code Codec}
 * bean. The codec is registered as an {@code @ApplicationScoped} bean and can be overridden by
 * providing a custom {@code Codec} bean in applications or tests.
 *
 * <p>Thread Safety: This codec is thread-safe. The Jackson {@code ObjectMapper} is thread-safe by
 * design.
 */
@ApplicationScoped
@DefaultBean
public class DefaultCodec implements Codec {

  private final ObjectMapper objectMapper;

  public DefaultCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Returns the content type of the default codec.
   *
   * @return {@code "application/json"}
   */
  @Override
  public String getContentType() {
    return "application/json";
  }

  /**
   * Encodes the given object into a JSON byte array.
   *
   * @param object The object to encode. Must be Jackson-compatible (POJO, record, etc.).
   * @return The JSON-encoded byte array.
   * @throws SerializationException if encoding fails.
   */
  @Override
  public byte[] encode(Object object) throws SerializationException {
    if (object == null) {
      throw new SerializationException("Cannot encode null object");
    }

    try {
      return objectMapper.writeValueAsBytes(object);
    } catch (JsonProcessingException e) {
      String className = object.getClass().getSimpleName();
      throw new SerializationException(
          "Failed to serialize " + className + ": " + e.getMessage(), e);
    }
  }

  /**
   * Decodes a JSON byte array into an object of the specified type.
   *
   * @param data The JSON byte array.
   * @param type The target class for deserialization.
   * @param ceType The CloudEvent type header (unused by default codec).
   * @return The deserialized object.
   * @throws DeserializationException if decoding fails.
   */
  @Override
  public Object decode(byte[] data, Class<?> type, String ceType) throws DeserializationException {
    if (data == null || data.length == 0) {
      throw new DeserializationException("Cannot decode null or empty data");
    }

    if (type == null) {
      throw new DeserializationException("Target type cannot be null");
    }

    try {
      return objectMapper.readValue(data, type);
    } catch (JsonProcessingException e) {
      String typeName = type.getSimpleName();
      throw new DeserializationException(
          "Failed to deserialize JSON to " + typeName + ": " + e.getMessage(), e);
    } catch (java.io.IOException e) {
      String typeName = type.getSimpleName();
      throw new DeserializationException(
          "IO error while deserializing JSON to " + typeName + ": " + e.getMessage(), e);
    }
  }
}
