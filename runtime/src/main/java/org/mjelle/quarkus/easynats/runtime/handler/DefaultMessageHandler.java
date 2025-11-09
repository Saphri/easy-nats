package org.mjelle.quarkus.easynats.runtime.handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Nullable;

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import org.mjelle.quarkus.easynats.runtime.NatsConfiguration;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;
import org.mjelle.quarkus.easynats.runtime.subscriber.CloudEventException;
import org.mjelle.quarkus.easynats.runtime.subscriber.CloudEventUnwrapper;
import org.mjelle.quarkus.easynats.runtime.subscriber.DefaultNatsMessage;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Default implementation of {@link MessageHandler}.
 *
 * <p>This handler invokes a subscriber method with the message payload and handles message
 * acknowledgment based on the outcome.
 *
 * <p>Supports both String payloads (004-nats-subscriber-mvp) and typed objects with CloudEvent
 * unwrapping and Jackson deserialization (006-typed-subscriber).
 */
public class DefaultMessageHandler implements MessageHandler {

  private static final Logger LOGGER = Logger.getLogger(DefaultMessageHandler.class);

  /**
   * Cache for JavaType objects to reduce reflection overhead during handler construction.
   *
   * <p>Keyed by "declaringBeanClass#methodName#paramTypes" to uniquely identify each subscriber
   * method signature, including overloaded methods. Stores computed JavaType objects
   * (parameterType, payloadType) which are expensive to construct via reflection.
   *
   * <p>This optimization reduces startup latency for applications with multiple subscribers by
   * eliminating redundant type construction calls.
   */
  private static final ConcurrentHashMap<String, CachedJavaTypes> TYPE_CACHE =
      new ConcurrentHashMap<>();

  /**
   * Holds cached JavaType objects for a subscriber method.
   *
   * @param parameterType the full parameter type (e.g., NatsMessage&lt;Order&gt; or Order)
   * @param payloadType the payload type to deserialize (e.g., Order)
   * @param isExplicitMode true if parameter is NatsMessage&lt;T&gt;, false otherwise
   */
  private record CachedJavaTypes(
      JavaType parameterType, JavaType payloadType, boolean isExplicitMode) {}

  private final SubscriberMetadata metadata;
  private final Object bean;
  private final Method method;
  private final ObjectMapper objectMapper;
  private final Codec codec;
  private final JavaType parameterType;
  private final boolean isExplicitMode;
  private final JavaType payloadType;
  private final NatsTraceService traceService;
  private final NatsConfiguration config;

  /**
   * Creates a new message handler.
   *
   * @param metadata the subscriber metadata
   * @param bean the bean instance containing the subscriber method
   * @param method the subscriber method
   * @param objectMapper the Jackson ObjectMapper for typed deserialization
   * @param codec the global payload codec for deserialization
   * @param config the NATS configuration for logging settings
   * @param traceService the OpenTelemetry tracing service (may be null if tracing not available)
   */
  public DefaultMessageHandler(
      SubscriberMetadata metadata,
      Object bean,
      Method method,
      ObjectMapper objectMapper,
      Codec codec,
      NatsConfiguration config,
      @Nullable NatsTraceService traceService) {
    this.metadata = metadata;
    this.bean = bean;
    this.method = method;
    this.objectMapper = objectMapper;
    this.codec = codec;
    this.config = config;
    this.traceService = traceService;

    // Use cached JavaType objects to reduce reflection overhead
    // Cache key includes parameter types to handle overloaded methods correctly
    String cacheKey = getCacheKey(metadata);
    CachedJavaTypes cachedTypes =
        TYPE_CACHE.computeIfAbsent(
            cacheKey,
            k -> {
              // Determine parameter type from the Method's generic parameter type
              // This avoids needing to Class.forName() user types which aren't available at
              // runtime
              Type genericParamType = method.getGenericParameterTypes()[0];
              JavaType paramType = objectMapper.getTypeFactory().constructType(genericParamType);

              // Detect if parameter type is NatsMessage<T> (explicit mode)
              // If so, extract T for deserialization; otherwise use full parameter type
              boolean explicitMode = isNatsMessageType(genericParamType);
              JavaType payloadType =
                  explicitMode ? extractPayloadType(genericParamType, objectMapper) : paramType;

              return new CachedJavaTypes(paramType, payloadType, explicitMode);
            });

    this.parameterType = cachedTypes.parameterType();
    this.payloadType = cachedTypes.payloadType();
    this.isExplicitMode = cachedTypes.isExplicitMode();
  }

  /**
   * Generates a cache key for the subscriber method.
   *
   * @param metadata the subscriber metadata
   * @return cache key in format "declaringBeanClass#methodName#paramType1,paramType2,..."
   */
  private static String getCacheKey(SubscriberMetadata metadata) {
    return metadata.declaringBeanClass()
        + "#"
        + metadata.methodName()
        + "#"
        + String.join(",", metadata.parameterTypes());
  }

  /**
   * Determines if the given type is NatsMessage or NatsMessage&lt;T&gt;.
   *
   * @param type the type to check
   * @return true if type is NatsMessage or NatsMessage&lt;T&gt;, false otherwise
   */
  private static boolean isNatsMessageType(Type type) {
    if (type instanceof ParameterizedType) {
      Type rawType = ((ParameterizedType) type).getRawType();
      return rawType instanceof Class && NatsMessage.class.isAssignableFrom((Class<?>) rawType);
    } else if (type instanceof Class) {
      return NatsMessage.class.isAssignableFrom((Class<?>) type);
    }
    return false;
  }

  /**
   * Extracts the payload type T from NatsMessage&lt;T&gt;.
   *
   * @param type the NatsMessage&lt;T&gt; type
   * @param objectMapper the Jackson ObjectMapper to use for type construction
   * @return the type T, or String if T cannot be determined
   */
  private static JavaType extractPayloadType(Type type, ObjectMapper objectMapper) {
    if (type instanceof ParameterizedType) {
      Type[] typeArgs = ((ParameterizedType) type).getActualTypeArguments();
      if (typeArgs.length > 0) {
        return objectMapper.getTypeFactory().constructType(typeArgs[0]);
      }
    }
    // Fallback to String if type parameter cannot be extracted
    return objectMapper.getTypeFactory().constructType(String.class);
  }

  @Override
  public void handle(Message message) {
    if (objectMapper == null) {
      LOGGER.errorf(
          "Message received for subject=%s after application shutdown. "
              + "Sending nak to retry delivery.",
          message.getSubject());
      try {
        message.nak(); // Ensure message is redelivered
      } catch (Exception e) {
        LOGGER.error("Failed to nak message during shutdown", e);
      }
      return;
    }
    // Create a consumer span for this message processing (if tracing is available)
    Span span = null;
    Scope scope = null;
    try {
      // Create tracing span if traceService is available
      if (traceService != null) {
        // Determine which subject to use for tracing
        String subject = metadata.isDurableConsumer() ? metadata.stream() : metadata.subject();
        span = traceService.createConsumerSpan(subject, message);
        if (span != null) {
          scope = traceService.activateSpan(span);
        }
      }

      Object payload;

      // 006-typed-subscriber: CloudEvent unwrap + typed deserialization
      // 009-explicit-ack-nak: Support both implicit (typed payload) and explicit (NatsMessage<T>)
      // modes
      byte[] eventData = null;
      try {
        // Step 1: Unwrap CloudEvent (binary-mode)
        eventData = CloudEventUnwrapper.unwrapData(message);

        // Step 2: Deserialize to typed object using the global codec
        // This handles complex types, generics, and user-defined classes
        // For explicit mode: deserialize to T (the payload type inside NatsMessage<T>)
        // For implicit mode: deserialize to the subscriber's parameter type directly
        try {
          // Get the target class from the JavaType
          Class<?> targetType = payloadType.getRawClass();
          // Get CloudEvent ce-type header for codec context
          String ceType =
              message.getHeaders() != null ? message.getHeaders().getFirst("ce-type") : null;
          // Use global codec for deserialization
          payload = codec.decode(eventData, targetType, ceType);
        } catch (Exception e) {
          throw new DeserializationException(
              """
              Failed to deserialize to type %s
              """
                  .formatted(payloadType.getTypeName()),
              e);
        }
      } catch (CloudEventException e) {
        LOGGER.errorf(
            "CloudEvent validation failed for subject=%s, method=%s, cause=%s",
            metadata.subject(), metadata.methodName(), e.getMessage());
        // Record error in span
        if (span != null) {
          traceService.recordException(span, e);
        }
        // Auto-nak message since it cannot be processed in either mode
        nakMessage(message);
        return;
      } catch (DeserializationException e) {
        // Log with detailed context for debugging
        // Check configuration to determine if payload should be included in logs
        if (config.logPayloadsOnError()) {
          String payloadPreview =
              truncatePayload(
                  eventData != null ? new String(eventData, StandardCharsets.UTF_8) : "[no data]",
                  500);
          LOGGER.errorf(
              "Message deserialization failed for subject=%s, method=%s, type=%s\n"
                  + "  Root cause: %s\n"
                  + "  Raw payload: %s",
              metadata.subject(),
              metadata.methodName(),
              payloadType.getTypeName(),
              e.getMessage(),
              payloadPreview);
        } else {
          LOGGER.errorf(
              "Message deserialization failed for subject=%s, method=%s, type=%s\n"
                  + "  Root cause: %s\n"
                  + "  (Payload logging disabled. Set quarkus.easynats.log-payloads-on-error=true to enable)",
              metadata.subject(), metadata.methodName(), payloadType.getTypeName(), e.getMessage());
        }
        // Record error in span
        if (span != null) {
          traceService.recordException(span, e);
        }
        // Auto-nak message since it cannot be processed in either mode
        nakMessage(message);
        return;
      }

      // Step 3: Wrap payload in NatsMessage<T> if explicit mode, otherwise use payload directly
      Object methodParam = isExplicitMode ? new DefaultNatsMessage<>(message, payload) : payload;

      // Step 4: Invoke method with payload or NatsMessage wrapper
      invokeSubscriberMethod(methodParam);

      // Step 5: Auto-ack on success (only for implicit mode; explicit mode is developer's
      // responsibility)
      if (!isExplicitMode) {
        message.ack();
      }
    } catch (InvocationTargetException e) {
      // The subscriber method threw an exception
      LOGGER.errorf(
          e.getCause(),
          "Error processing message for subscriber: subject=%s, method=%s",
          metadata.subject(),
          metadata.methodName());
      // Record error in span
      if (span != null) {
        traceService.recordException(span, e.getCause());
      }
      // Only auto-nak if implicit mode; explicit mode developer handles it
      if (!isExplicitMode) {
        nakMessage(message);
      }
    } catch (Exception e) {
      // Other errors (reflection, etc.)
      LOGGER.errorf(
          e,
          "Error processing message for subscriber: subject=%s, method=%s",
          metadata.subject(),
          metadata.methodName());
      // Record error in span
      if (span != null) {
        traceService.recordException(span, e);
      }
      // Only auto-nak if implicit mode; explicit mode developer handles it
      if (!isExplicitMode) {
        nakMessage(message);
      }
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
   * Invokes the subscriber method with the given payload.
   *
   * @param payload the message payload (String or typed object)
   * @throws IllegalAccessException if the method is not accessible
   * @throws InvocationTargetException if the method throws an exception
   */
  private void invokeSubscriberMethod(Object payload)
      throws IllegalAccessException, InvocationTargetException {
    method.invoke(bean, payload);
  }

  /**
   * Negatively acknowledges a message.
   *
   * @param message the message to nak
   */
  private void nakMessage(Message message) {
    try {
      message.nak();
    } catch (Exception nakError) {
      LOGGER.errorf(
          nakError,
          "Failed to nak message for subject=%s, method=%s",
          metadata.subject(),
          metadata.methodName());
    }
  }

  /**
   * Truncates a payload string to a maximum length for logging.
   *
   * @param payload the payload string
   * @param maxLength the maximum length
   * @return truncated payload with ellipsis if needed
   */
  private String truncatePayload(String payload, int maxLength) {
    if (payload == null) {
      return "[null]";
    }
    if (payload.length() > maxLength) {
      return payload.substring(0, maxLength) + "... [truncated]";
    }
    return payload;
  }
}
