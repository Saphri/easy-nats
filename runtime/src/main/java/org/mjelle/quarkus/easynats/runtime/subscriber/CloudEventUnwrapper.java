package org.mjelle.quarkus.easynats.runtime.subscriber;

import io.nats.client.Message;

/**
 * Utility for unwrapping CloudEvents 1.0 binary-mode messages.
 *
 * <p>CloudEvents binary-mode format stores: - Attributes (ce-specversion, ce-type, ce-source,
 * ce-id, etc.) in NATS message headers with "ce-" prefix - Event data in NATS message payload
 *
 * <p>This class validates that a NATS message is a valid CloudEvents 1.0 binary-mode message and
 * extracts the event data for further processing.
 */
public class CloudEventUnwrapper {

  private static final String CE_SPECVERSION_HEADER = "ce-specversion";
  private static final String CE_TYPE_HEADER = "ce-type";
  private static final String CE_SOURCE_HEADER = "ce-source";
  private static final String CE_ID_HEADER = "ce-id";
  private static final String SPEC_VERSION_1_0 = "1.0";

  /**
   * Unwrap a NATS message (CloudEvents binary-mode) to extract event data.
   *
   * <p>Validates that the message contains all required CloudEvents 1.0 binary-mode headers
   * (ce-specversion, ce-type, ce-source, ce-id) and that ce-specversion is "1.0".
   *
   * @param message NATS message with ce-* headers (binary-mode CloudEvents)
   * @return extracted event data bytes from message payload
   * @throws CloudEventException if message is not valid CloudEvents 1.0 binary-mode
   */
  public static byte[] unwrapData(Message message) throws CloudEventException {
    if (message == null) {
      throw new CloudEventException("Message cannot be null");
    }

    // Validate required headers present
    String specVersion = getHeaderValue(message, CE_SPECVERSION_HEADER);
    if (specVersion == null) {
      throw new CloudEventException(
          "Missing required CloudEvents header: " + CE_SPECVERSION_HEADER);
    }

    String type = getHeaderValue(message, CE_TYPE_HEADER);
    if (type == null) {
      throw new CloudEventException("Missing required CloudEvents header: " + CE_TYPE_HEADER);
    }

    String source = getHeaderValue(message, CE_SOURCE_HEADER);
    if (source == null) {
      throw new CloudEventException("Missing required CloudEvents header: " + CE_SOURCE_HEADER);
    }

    String id = getHeaderValue(message, CE_ID_HEADER);
    if (id == null) {
      throw new CloudEventException("Missing required CloudEvents header: " + CE_ID_HEADER);
    }

    // Validate spec version
    if (!SPEC_VERSION_1_0.equals(specVersion)) {
      throw new CloudEventException(
          "Unsupported CloudEvents version: " + specVersion + " (expected 1.0)");
    }

    // Extract and return payload (event data)
    byte[] payload = message.getData();
    if (payload == null) {
      throw new CloudEventException("Message payload is null or empty");
    }

    return payload;
  }

  /**
   * Gets a header value from NATS message headers.
   *
   * @param message the NATS message
   * @param headerName the header name
   * @return the header value, or null if not present
   */
  private static String getHeaderValue(Message message, String headerName) {
    java.util.List<String> values = message.getHeaders().get(headerName);
    return (values != null && !values.isEmpty()) ? values.get(0) : null;
  }
}
