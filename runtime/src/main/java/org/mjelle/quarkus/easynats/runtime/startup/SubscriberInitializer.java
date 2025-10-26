package org.mjelle.quarkus.easynats.runtime.startup;

import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.lang.reflect.Method;
import java.time.Duration;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistry;
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

    /**
     * Creates a new subscriber initializer.
     *
     * @param subscriberRegistry the subscriber registry containing build-time metadata
     * @param connectionManager the NATS connection manager
     */
    public SubscriberInitializer(
            SubscriberRegistry subscriberRegistry, NatsConnectionManager connectionManager) {
        this.subscriberRegistry = subscriberRegistry;
        this.connectionManager = connectionManager;
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
    private void initializeSubscriber(SubscriberMetadata metadata) throws Exception {
        LOGGER.infof(
                "Initializing subscription: subject=%s, method=%s",
                metadata.subject(), metadata.methodName());

        JetStream js = connectionManager.getJetStream();

        // Create the ephemeral subscriber (consumer)
        JetStreamSubscription subscription = js.subscribe(metadata.subject());

        // Get the bean instance and method from the registry
        Object bean = getBeanInstance(metadata);
        Method method = getSubscriberMethod(bean, metadata);

        // Create the message handler
        DefaultMessageHandler handler = new DefaultMessageHandler(metadata, bean, method);

        // Start a thread to listen for messages on this subscription
        startSubscriberThread(subscription, handler, metadata);

        LOGGER.infof(
                "Successfully created subscription: subject=%s, method=%s.%s",
                metadata.subject(),
                metadata.declaringBeanClass(),
                metadata.methodName());
    }

    /**
     * Starts a daemon thread to listen for messages on a subscription.
     *
     * @param subscription the subscription to listen on
     * @param handler the message handler
     * @param metadata the subscriber metadata
     */
    private void startSubscriberThread(
            JetStreamSubscription subscription,
            DefaultMessageHandler handler,
            SubscriberMetadata metadata) {
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                while (!Thread.currentThread().isInterrupted()) {
                                    Message message = subscription.nextMessage(Duration.ofSeconds(1));
                                    if (message != null) {
                                        handler.handle(message);
                                    }
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LOGGER.infof(
                                        "Subscriber thread interrupted for subject=%s",
                                        metadata.subject());
                            } catch (Exception e) {
                                LOGGER.errorf(
                                        e,
                                        "Error in subscriber thread for subject=%s",
                                        metadata.subject());
                            }
                        });
        thread.setName("NATS-Subscriber-" + metadata.subject());
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Gets the bean instance for a subscriber.
     *
     * <p>
     * This is a placeholder that would normally look up the bean from the CDI container. For
     * now, this should be populated by the build processor.
     * </p>
     *
     * @param metadata the subscriber metadata
     * @return the bean instance
     */
    private Object getBeanInstance(SubscriberMetadata metadata) {
        // This would be populated by the build processor with actual bean instances
        // For now, we throw an error as a placeholder
        throw new UnsupportedOperationException(
                "Bean lookup not yet implemented. This should be populated by build processor.");
    }

    /**
     * Gets the subscriber method from a bean.
     *
     * @param bean the bean instance
     * @param metadata the subscriber metadata
     * @return the subscriber method
     * @throws NoSuchMethodException if the method is not found
     */
    private Method getSubscriberMethod(Object bean, SubscriberMetadata metadata)
            throws NoSuchMethodException {
        return bean.getClass().getMethod(metadata.methodName(), String.class);
    }
}
