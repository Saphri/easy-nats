package org.mjelle.quarkus.easynats.runtime.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.ConsumerConfiguration;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.lang.reflect.Method;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistry;
import org.mjelle.quarkus.easynats.runtime.consumer.EphemeralConsumerFactory;
import org.mjelle.quarkus.easynats.runtime.handler.DefaultMessageHandler;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;

/**
 * Initializes subscriber consumers at application startup.
 *
 * <p>
 * This bean listens for the {@link StartupEvent} and initializes consumers for all
 * subscriber methods discovered at build time. It supports two modes:
 * </p>
 * <ul>
 * <li><strong>Ephemeral</strong>: Creates ephemeral consumers dynamically for subjects</li>
 * <li><strong>Durable</strong>: Verifies that pre-configured durable consumers exist on NATS server</li>
 * </ul>
 *
 * <p>
 * Registers message handlers via the {@code consumerContext.consume()} API for both modes.
 * </p>
 *
 * <p>
 * If any consumer creation or verification fails, the application startup fails with a clear error message.
 * </p>
 */
@ApplicationScoped
public class SubscriberInitializer {

    private static final Logger LOGGER = Logger.getLogger(SubscriberInitializer.class);

    private final SubscriberRegistry subscriberRegistry;
    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new subscriber initializer.
     *
     * @param subscriberRegistry the subscriber registry containing build-time metadata
     * @param connectionManager the NATS connection manager
     * @param objectMapper the Jackson ObjectMapper for typed deserialization
     */
    public SubscriberInitializer(
            SubscriberRegistry subscriberRegistry, NatsConnectionManager connectionManager,
            ObjectMapper objectMapper) {
        this.subscriberRegistry = subscriberRegistry;
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Initializes subscribers on application startup.
     *
     * @param event the startup event (not used)
     * @throws IllegalStateException if any subscriber initialization fails
     */
    void onStart(@Observes StartupEvent event) {
        LOGGER.info("Initializing NATS subscribers");

        for (SubscriberMetadata metadata : subscriberRegistry.getSubscribers()) {
            try {
                initializeSubscriber(metadata);
            } catch (Exception e) {
                String errorContext = metadata.isDurableConsumer() ?
                        String.format("durable consumer: stream=%s, consumer=%s",
                                metadata.stream(), metadata.consumer()) :
                        String.format("subject: %s", metadata.subject());
                throw new IllegalStateException(
                        "Failed to initialize subscriber for " + errorContext, e);
            }
        }

        LOGGER.infof(
                "Successfully initialized %d NATS subscribers",
                subscriberRegistry.getSubscribers().size());
    }

    /**
     * Initializes a single subscriber.
     *
     * @param metadata the subscriber metadata
     * @throws Exception if consumer creation, verification, or handler registration fails
     */
    private void initializeSubscriber(SubscriberMetadata metadata)
            throws Exception, ClassNotFoundException {

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
            LOGGER.debugf("OpenTelemetry tracing not available: %s", e.getMessage());
        }

        DefaultMessageHandler handler = (traceService != null) ?
                new DefaultMessageHandler(metadata, bean, method, objectMapper, traceService) :
                new DefaultMessageHandler(metadata, bean, method, objectMapper);

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
                LOGGER.infof("Verified durable consumer: stream=%s, consumer=%s",
                        metadata.stream(), metadata.consumer());
            } catch (io.nats.client.JetStreamApiException e) {
                throw new IllegalStateException(
                        String.format("""
                                Failed to verify durable consumer: Stream '%s' does not contain consumer '%s'.
                                Please ensure the consumer is pre-configured on the NATS server.""",
                                metadata.stream(), metadata.consumer()), e);
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
        consumerContext.consume(handler::handle);

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
                    metadata.subject(),
                    streamName,
                    metadata.declaringBeanClass(),
                    metadata.methodName());
        }
    }

    /**
     * Gets the bean instance for a subscriber.
     *
     * <p>
     * Looks up the CDI bean from the Arc container using the class name from the metadata.
     * </p>
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
     * <p>
     * Uses the JetStream management API to find which stream contains the subject.
     * Fails fast if no stream or multiple streams are found.
     * </p>
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
                    "No JetStream stream found for subject: " + subject);
        }

        if (streams.size() > 1) {
            throw new IllegalStateException(
                    "Multiple streams found for subject '" + subject + "': " + streams
                            + ". Cannot determine which stream to use.");
        }

        return streams.get(0);
    }

    /**
     * Gets the subscriber method from a bean.
     *
     * <p>
     * Looks up the method by name and parameter count. This avoids needing to load
     * parameter types which may not be available at runtime (e.g., user-defined classes
     * from integration tests). The actual parameter type is determined at runtime from
     * the Method's generic type information.
     * </p>
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
            if (method.getName().equals(metadata.methodName()) &&
                method.getParameterCount() == expectedParamCount) {
                method.setAccessible(true);
                return method;
            }
        }

        throw new NoSuchMethodException(
                "Method " + metadata.methodName() + " with " + expectedParamCount +
                " parameter(s) not found in class " + metadata.declaringBeanClass());
    }
}
