package org.mjelle.quarkus.easynats.runtime.startup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.runtime.NatsConfiguration;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistry;
import org.mjelle.quarkus.easynats.runtime.consumer.EphemeralConsumerFactory;
import org.mjelle.quarkus.easynats.runtime.handler.DefaultMessageHandler;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.nats.client.ConsumerContext;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.MessageConsumer;
import io.nats.client.api.ConsumerConfiguration;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/**
 * Initializes subscriber consumers at application startup.
 *
 * <p>This bean listens for the {@link StartupEvent} and initializes consumers for all subscriber
 * methods discovered at build time. It supports two modes:
 *
 * <ul>
 *   <li><strong>Ephemeral</strong>: Creates ephemeral consumers dynamically for subjects
 *   <li><strong>Durable</strong>: Verifies that pre-configured durable consumers exist on NATS
 *       server
 * </ul>
 *
 * <p>Registers message handlers via the {@code consumerContext.consume()} API for both modes.
 *
 * <p>If any consumer creation or verification fails, the application startup fails with a clear
 * error message.
 */
@ApplicationScoped
public class SubscriberInitializer {

  private static final Logger LOGGER = Logger.getLogger(SubscriberInitializer.class);

  private final SubscriberRegistry subscriberRegistry;
  private final NatsConnectionManager connectionManager;
  private final ObjectMapper objectMapper;
  private final Codec codec;
  private final NatsConfiguration config;
  private final List<MessageConsumer> consumers = new ArrayList<>();

  /**
   * Creates a new subscriber initializer.
   *
   * @param subscriberRegistry the subscriber registry containing build-time metadata
   * @param connectionManager the NATS connection manager
   * @param objectMapper the Jackson ObjectMapper for typed deserialization
   * @param codec the global payload codec (injected by Quarkus)
   * @param config the NATS configuration for logging settings
   */
  public SubscriberInitializer(
      SubscriberRegistry subscriberRegistry,
      NatsConnectionManager connectionManager,
      ObjectMapper objectMapper,
      Codec codec,
      NatsConfiguration config) {
    this.subscriberRegistry = subscriberRegistry;
    this.connectionManager = connectionManager;
    this.objectMapper = objectMapper;
    this.codec = codec;
    this.config = config;
  }

  /**
   * Initializes subscribers on application startup. Runs after StreamInitializer (@Priority(10)) to
   * ensure streams exist first.
   *
   * @param event the startup event (not used)
   */
  void onStart(@Observes @Priority(100) StartupEvent event) {
    LOGGER.info(
        "Initializing NATS subscribers on startup (priority 100 - runs after streams are created)");
    try {
      config.validate();
      initializeAllSubscribers();
    } catch (Exception e) {
      LOGGER.errorf(e, "Failed to initialize NATS subscribers at startup");
      throw new IllegalStateException(
          """
          Failed to initialize NATS subscribers at startup
          """,
          e);
    }
  }

  /**
   * Initializes all registered subscribers.
   *
   * @throws IllegalStateException if any subscriber initialization fails
   */
  private void initializeAllSubscribers() {
    for (SubscriberMetadata metadata : subscriberRegistry.getSubscribers()) {
      try {
        initializeSubscriber(metadata);
      } catch (Exception e) {
        throw new IllegalStateException(
            metadata.isDurableConsumer()
                ? """
                Failed to initialize subscriber for durable consumer: stream=%s, consumer=%s
                """
                    .formatted(metadata.stream(), metadata.consumer())
                : """
                Failed to initialize subscriber for subject: %s
                """
                    .formatted(metadata.subject()),
            e);
      }
    }

    LOGGER.infof(
        "Successfully initialized %d NATS subscribers", subscriberRegistry.getSubscribers().size());
  }

  /**
   * Stops all NATS subscribers on application shutdown.
   *
   * @param event the shutdown event (not used)
   */
  void onStop(@Observes @Priority(10) ShutdownEvent event) {
    LOGGER.info("Stopping NATS subscribers");
    for (MessageConsumer consumer : consumers) {
      try {
        consumer.stop();
      } catch (Exception e) {
        LOGGER.error("Error stopping NATS consumer", e);
      }
    }
    consumers.clear();
    LOGGER.infof("Successfully stopped subscribers", consumers.size());
  }

  /**
   * Initializes a single subscriber.
   *
   * @param metadata the subscriber metadata
   * @throws Exception if consumer creation, verification, or handler registration fails
   */
  private void initializeSubscriber(SubscriberMetadata metadata) throws Exception {

    // Get the bean instance and method from the registry
    Object bean = getBeanInstance(metadata);
    Method method = getSubscriberMethod(bean, metadata);

    // Create the message handler with ObjectMapper for typed deserialization
    // Tracing will be added if traceService is available in the container
    NatsTraceService traceService = null;
    try {
      traceService = Arc.container().instance(NatsTraceService.class).get();
    } catch (Exception e) {
      // Tracing not available; continue without it
    }

    DefaultMessageHandler handler =
        new DefaultMessageHandler(
            metadata, bean, method, objectMapper, codec, config, traceService);

    JetStreamManagement jsm = connectionManager.getConnection().jetStreamManagement();
    JetStream js = connectionManager.getJetStream();
    String streamName;
    io.nats.client.api.ConsumerInfo consumerInfo;

    if (metadata.isDurableConsumer()) {
      // Durable mode: verify consumer exists on NATS server
      LOGGER.infof(
          "Initializing durable subscription: stream=%s, consumer=%s, method=%s",
          metadata.stream(), metadata.consumer(), metadata.methodName());

      streamName = metadata.stream();
      try {
        consumerInfo = jsm.getConsumerInfo(metadata.stream(), metadata.consumer());
        LOGGER.infof(
            "Verified durable consumer: stream=%s, consumer=%s",
            metadata.stream(), metadata.consumer());
      } catch (io.nats.client.JetStreamApiException e) {
        throw new IllegalStateException(
            """
            Failed to verify durable consumer: Stream '%s' does not contain consumer '%s'.
            Please ensure the consumer is pre-configured on the NATS server.
            """
                .formatted(metadata.stream(), metadata.consumer()),
            e);
      }
    } else {
      // Ephemeral mode: create ephemeral consumer dynamically
      LOGGER.infof(
          "Initializing ephemeral subscription: subject=%s, method=%s",
          metadata.subject(), metadata.methodName());

      // Resolve the stream name for the subject
      streamName = resolveStreamName(metadata.subject());
      LOGGER.debugf("Resolved stream name: %s for subject: %s", streamName, metadata.subject());

      // Create ephemeral consumer configuration
      ConsumerConfiguration consumerConfig =
          EphemeralConsumerFactory.createEphemeralConsumerConfig(metadata.subject());

      // Create the ephemeral consumer using JetStreamManagement
      consumerInfo = jsm.addOrUpdateConsumer(streamName, consumerConfig);
    }

    // Both paths: get ConsumerContext and consume
    ConsumerContext consumerContext = js.getConsumerContext(streamName, consumerInfo.getName());

    // Start consuming messages using the ConsumerContext API
    MessageConsumer consumer = consumerContext.consume(handler::handle);

    if (metadata.isDurableConsumer()) {
      LOGGER.infof(
          "Successfully initialized durable subscription: stream=%s, consumer=%s, method=%s.%s",
          metadata.stream(),
          metadata.consumer(),
          metadata.declaringBeanClass(),
          metadata.methodName());
    } else {
      LOGGER.infof(
          "Successfully initialized ephemeral subscription: subject=%s, stream=%s, method=%s.%s",
          metadata.subject(), streamName, metadata.declaringBeanClass(), metadata.methodName());
    }

    // Only add consumer to list after all operations complete successfully
    consumers.add(consumer);
  }

  /**
   * Gets the bean instance for a subscriber.
   *
   * <p>Looks up the CDI bean from the Arc container using the class name from the metadata.
   *
   * @param metadata the subscriber metadata
   * @return the bean instance
   * @throws ClassNotFoundException if the bean class cannot be loaded
   */
  private Object getBeanInstance(SubscriberMetadata metadata) throws ClassNotFoundException {
    String className = metadata.declaringBeanClass();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> beanClass = classLoader.loadClass(className);
    return Arc.container().instance(beanClass).get();
  }

  /**
   * Resolves the stream name for a given subject.
   *
   * <p>Uses the JetStream management API to find which stream contains the subject. Fails fast if
   * no stream or multiple streams are found.
   *
   * @param subject the NATS subject
   * @return the stream name
   * @throws IllegalStateException if no stream or multiple streams are found
   */
  private String resolveStreamName(String subject) throws Exception {
    JetStreamManagement jsm = connectionManager.getConnection().jetStreamManagement();
    java.util.List<String> streams = jsm.getStreamNames(subject);

    if (streams.isEmpty()) {
      throw new IllegalStateException(
          """
          No JetStream stream found for subject: %s
          """
              .formatted(subject));
    }

    if (streams.size() > 1) {
      throw new IllegalStateException(
          """
          Multiple streams found for subject '%s': %s.
          Cannot determine which stream to use.
          """
              .formatted(subject, streams));
    }

    return streams.get(0);
  }

  /**
   * Gets the subscriber method from a bean.
   *
   * <p>Looks up the method by name and parameter count. This avoids needing to load parameter types
   * which may not be available at runtime (e.g., user-defined classes from integration tests). The
   * actual parameter type is determined at runtime from the Method's generic type information.
   *
   * @param bean the bean instance
   * @param metadata the subscriber metadata
   * @return the subscriber method
   * @throws NoSuchMethodException if the method is not found
   * @throws ClassNotFoundException if the bean class cannot be loaded
   */
  private Method getSubscriberMethod(Object bean, SubscriberMetadata metadata)
      throws NoSuchMethodException, ClassNotFoundException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> beanClass = classLoader.loadClass(metadata.declaringBeanClass());

    // Find method by name and parameter count only
    // Avoid loading parameter types which may not be available at runtime
    int expectedParamCount = metadata.parameterTypes().size();

    for (Method method : beanClass.getDeclaredMethods()) {
      if (method.getName().equals(metadata.methodName())
          && method.getParameterCount() == expectedParamCount) {
        method.setAccessible(true);
        return method;
      }
    }

    throw new NoSuchMethodException(
        """
        Method %s with %d parameter(s) not found in class %s
        """
            .formatted(metadata.methodName(), expectedParamCount, metadata.declaringBeanClass()));
  }
}
