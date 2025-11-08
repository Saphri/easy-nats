package org.mjelle.quarkus.easynats;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.SerializationException;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;
import org.mjelle.quarkus.easynats.runtime.subscriber.TypeValidationResult;
import org.mjelle.quarkus.easynats.runtime.subscriber.TypeValidator;

import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Generic injectable wrapper for publishing typed messages to NATS JetStream as CloudEvents.
 *
 * <p>All messages published through this class are automatically wrapped in the CloudEvents 1.0
 * format. Supports both primitive types (native encoding) and complex types (Jackson serialization)
 * for the payload. The CloudEvents metadata is generated automatically and includes: -
 * ce-specversion: "1.0" - ce-type: The fully-qualified class name of the payload - ce-source: The
 * application name (from quarkus.application.name), hostname, or "localhost" - ce-id:
 * Auto-generated UUID - ce-time: Current timestamp in ISO 8601 UTC format - ce-datacontenttype: The
 * value returned by the global codec's getContentType() method (default: "application/json")
 *
 * @param <T> the type of payload to publish
 */
@Dependent
public class NatsPublisher<T> {

  private final NatsConnectionManager connectionManager;
  private final Codec codec;
  private final String subject;
  private final AtomicBoolean typeValidated = new AtomicBoolean(false);
  private final NatsTraceService traceService;

  /**
   * Constructor for dependency injection without tracing (for test compatibility).
   *
   * @param connectionManager the NATS connection manager (injected by Quarkus)
   * @param codec the global payload codec (injected by Quarkus)
   */
  public NatsPublisher(NatsConnectionManager connectionManager, Codec codec) {
    this(connectionManager, codec, null, null);
  }

  /**
   * Constructor for dependency injection with a default subject.
   *
   * @param connectionManager the NATS connection manager (injected by Quarkus)
   * @param codec the global payload codec (injected by Quarkus)
   * @param subject the default NATS subject for this publisher
   */
  public NatsPublisher(NatsConnectionManager connectionManager, Codec codec, String subject) {
    this(connectionManager, codec, null, subject);
  }

  /**
   * Constructor for dependency injection with tracing.
   *
   * @param connectionManager the NATS connection manager (injected by Quarkus)
   * @param codec the global payload codec (injected by Quarkus)
   * @param traceService the tracing service (injected by Quarkus)
   */
  @Inject
  public NatsPublisher(
      NatsConnectionManager connectionManager, Codec codec, NatsTraceService traceService) {
    this(connectionManager, codec, traceService, null);
  }

  /**
   * Constructor for dependency injection with tracing and a default subject.
   *
   * @param connectionManager the NATS connection manager (injected by Quarkus)
   * @param codec the global payload codec (injected by Quarkus)
   * @param traceService the tracing service (injected by Quarkus)
   * @param subject the default NATS subject for this publisher
   */
  public NatsPublisher(
      NatsConnectionManager connectionManager,
      Codec codec,
      NatsTraceService traceService,
      String subject) {
    this.connectionManager = connectionManager;
    this.codec = codec;
    this.traceService = traceService;
    this.subject = subject;
  }

  /**
   * Publishes a typed payload to the default NATS subject as a CloudEvent. The default subject must
   * be configured via @NatsSubject.
   *
   * <p>The payload is automatically wrapped in CloudEvents 1.0 format with: - ce-specversion: "1.0"
   * - ce-type: The fully-qualified class name of the payload - ce-source: The application name
   * (from quarkus.application.name), hostname, or "localhost" - ce-id: Auto-generated UUID -
   * ce-time: Current timestamp in ISO 8601 UTC format - ce-datacontenttype: The value returned by
   * the global codec's getContentType() method
   *
   * <p>The payload is encoded using the global codec (default: Jackson JSON encoder).
   *
   * @param payload the object to publish (must not be null)
   * @throws IllegalArgumentException if payload is null
   * @throws PublishingException if the default subject is not configured or if publication fails
   */
  public void publish(T payload) throws PublishingException {
    validateDefaultSubject();
    publish(this.subject, payload);
  }

  /**
   * Publishes a typed payload to the specified NATS subject as a CloudEvent.
   *
   * <p>The payload is automatically wrapped in CloudEvents 1.0 format with: - ce-specversion: "1.0"
   * - ce-type: The fully-qualified class name of the payload - ce-source: The application name
   * (from quarkus.application.name), hostname, or "localhost" - ce-id: Auto-generated UUID -
   * ce-time: Current timestamp in ISO 8601 UTC format - ce-datacontenttype: The value returned by
   * the global codec's getContentType() method
   *
   * <p>The payload is encoded using the global codec (default: Jackson JSON encoder).
   *
   * @param subject the NATS subject to publish to
   * @param payload the object to publish (must not be null)
   * @throws PublishingException if publication fails (payload is null, serialization error,
   *     connection error, broker unreachable, etc.)
   */
  public void publish(String subject, T payload) throws PublishingException {
    if (payload == null) {
      throw new PublishingException("Cannot publish null object");
    }

    // Validate type parameter T on first publish call
    validateTypeOnce();

    Span span = null;
    Scope scope = null;
    try {
      byte[] encodedPayload = encodePayload(payload);
      String contentType = codec.getContentType();
      CloudEventsHeaders.HeadersWithMetadata hwm =
          CloudEventsHeaders.createHeadersWithMetadata(payload.getClass(), contentType, null, null);

      // Create a tracing span for this publish operation
      if (traceService != null) {
        span = traceService.createProducerSpan(subject, hwm.headers);
        if (span != null) {
          scope = traceService.activateSpan(span);
        }
      }

      JetStream jetStream = connectionManager.getJetStream();
      jetStream.publish(subject, hwm.headers, encodedPayload);
    } catch (IOException | JetStreamApiException | SerializationException e) {
      // Record the exception in the span before throwing
      if (traceService != null && span != null) {
        traceService.recordException(span, e);
      }
      // Wrap other exceptions (NATS connection, broker errors, etc.)
      throw new PublishingException("Failed to publish message to subject '" + subject + "'", e);
    } finally {
      // Clean up span and scope
      if (scope != null) {
        scope.close();
      }
      if (span != null) {
        span.end();
      }
    }
  }

  /**
   * Validates the generic type parameter T on the first publish call. Uses AtomicBoolean to ensure
   * validation happens only once (thread-safe).
   *
   * @throws IllegalArgumentException if type T is not Jackson-compatible
   */
  private void validateTypeOnce() {
    if (!typeValidated.getAndSet(true)) {
      // First time - validate the type
      Class<T> typeClass = extractGenericType();
      if (typeClass != null) {
        TypeValidator validator = new TypeValidator();
        TypeValidationResult result = validator.validate(typeClass);

        if (!result.isValid()) {
          String errorMsg =
              String.format(
                  "Invalid type '%s' for NatsPublisher: %s",
                  typeClass.getSimpleName(), result.getErrorMessage());
          throw new IllegalArgumentException(errorMsg);
        }
      }
    }
  }

  /**
   * Extracts the generic type parameter T from NatsPublisher<T>. Uses Java reflection to retrieve
   * type information.
   *
   * @return the Class object for type T, or null if it cannot be determined
   */
  @SuppressWarnings("unchecked")
  private Class<T> extractGenericType() {
    try {
      Type genericSuperclass = getClass().getGenericSuperclass();
      if (genericSuperclass instanceof ParameterizedType pt) {
        Type[] typeArgs = pt.getActualTypeArguments();
        if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?>) {
          return (Class<T>) typeArgs[0];
        }
      }
    } catch (Exception e) {
      // If extraction fails, that's OK - type validation will be skipped
    }
    return null;
  }

  /**
   * Encode a payload using the global codec.
   *
   * @param payload the object to encode
   * @return the encoded byte array
   * @throws SerializationException if codec encoding fails
   */
  private byte[] encodePayload(T payload) throws SerializationException {
    return codec.encode(payload);
  }

  /**
   * Validates that a default subject is configured for this publisher.
   *
   * @throws PublishingException if the default subject is not configured
   */
  private void validateDefaultSubject() throws PublishingException {
    if (this.subject == null || this.subject.trim().isEmpty()) {
      throw new PublishingException(
          "Default NATS subject is not configured for this publisher. Use @NatsSubject or provide the subject dynamically.");
    }
  }
}
