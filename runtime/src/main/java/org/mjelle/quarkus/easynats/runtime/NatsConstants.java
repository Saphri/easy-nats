package org.mjelle.quarkus.easynats.runtime;

/**
 * Constants used throughout the NATS extension.
 *
 * <p>Centralizes magic strings and configuration keys to improve maintainability and prevent typos.
 */
public final class NatsConstants {

  // OpenTelemetry semantic conventions for messaging systems
  // See: https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/
  public static final String MESSAGING_SYSTEM = "messaging.system";
  public static final String MESSAGING_SYSTEM_VALUE = "nats";
  public static final String MESSAGING_DESTINATION = "messaging.destination";
  public static final String MESSAGING_OPERATION = "messaging.operation";

  // Messaging operations
  public static final String OPERATION_PUBLISH = "publish";
  public static final String OPERATION_RECEIVE = "receive";

  // Span name prefixes for distributed tracing
  public static final String SPAN_NAME_PUBLISH_PREFIX = "NATS publish to ";
  public static final String SPAN_NAME_RECEIVE_PREFIX = "NATS receive from ";

  // Configuration property keys
  public static final String CONFIG_SERVERS = "quarkus.easynats.servers";
  public static final String CONFIG_USERNAME = "quarkus.easynats.username";
  public static final String CONFIG_PASSWORD = "quarkus.easynats.password";
  public static final String CONFIG_TLS_NAME = "quarkus.easynats.tls-configuration-name";
  public static final String CONFIG_LOG_PAYLOADS = "quarkus.easynats.log-payloads-on-error";

  private NatsConstants() {
    // Utility class - prevent instantiation
    throw new AssertionError("Utility class - do not instantiate");
  }
}
