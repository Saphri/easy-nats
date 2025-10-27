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

/**
 * Initializes subscriber consumers at application startup.
 *
 * <p>
 * This bean listens for the {@link StartupEvent} and creates ephemeral consumers for all
 * subscriber methods discovered at build time. It registers message handlers via the
 * {@code consumerContext.consume()} API.
 * </p>
 *
 * <p>
 * If any consumer creation fails, the application startup fails with a clear error message.
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
                throw new IllegalStateException(
                        "Failed to initialize subscriber for subject: " + metadata.subject(), e);
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
     * @throws Exception if consumer creation or handler registration fails
     */
    private void initializeSubscriber(SubscriberMetadata metadata)
            throws Exception, ClassNotFoundException {
        LOGGER.infof(
                "Initializing subscription: subject=%s, method=%s",
                metadata.subject(), metadata.methodName());

        // Resolve the stream name for the subject
        String streamName = resolveStreamName(metadata.subject());
        LOGGER.debugf("Resolved stream name: %s for subject: %s", streamName, metadata.subject());

        // Get the bean instance and method from the registry
        Object bean = getBeanInstance(metadata);
        Method method = getSubscriberMethod(bean, metadata);

        // Create the message handler with ObjectMapper for typed deserialization
        DefaultMessageHandler handler = new DefaultMessageHandler(metadata, bean, method,
                objectMapper);

        // Create ephemeral consumer configuration
        ConsumerConfiguration consumerConfig = EphemeralConsumerFactory.createEphemeralConsumerConfig(metadata.subject());

        // Create the ephemeral consumer using JetStreamManagement
        JetStreamManagement jsm = connectionManager.getConnection().jetStreamManagement();
        io.nats.client.api.ConsumerInfo consumerInfo = jsm.addOrUpdateConsumer(streamName, consumerConfig);

        // Get the consumer context and start consuming
        JetStream js = connectionManager.getJetStream();
        ConsumerContext consumerContext = js.getConsumerContext(streamName, consumerInfo.getName());

        // Start consuming messages using the ConsumerContext API
        consumerContext.consume(handler::handle);

        LOGGER.infof(
                "Successfully created subscription: subject=%s, stream=%s, method=%s.%s",
                metadata.subject(),
                streamName,
                metadata.declaringBeanClass(),
                metadata.methodName());
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
