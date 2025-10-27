package org.mjelle.quarkus.easynats.runtime.metadata;

import java.util.List;

/**
 * Runtime metadata for a subscriber method.
 *
 * <p>
 * This record holds information about a method annotated with {@code @NatsSubscriber} that is
 * discovered at build time by the Jandex scanner and passed to runtime.
 * </p>
 *
 * @param subject the NATS subject to subscribe to (ephemeral mode)
 * @param stream the NATS JetStream stream name (durable mode)
 * @param consumer the NATS JetStream durable consumer name (durable mode)
 * @param methodClass the fully qualified class name containing the subscriber method
 * @param methodName the name of the subscriber method
 * @param declaringBeanClass the fully qualified class name of the CDI bean
 * @param parameterTypes a list of fully qualified parameter type names for the subscriber method
 */
public record SubscriberMetadata(
        String subject,
        String stream,
        String consumer,
        String methodClass,
        String methodName,
        String declaringBeanClass,
        List<String> parameterTypes) {

    /**
     * Determines if this metadata represents a durable consumer subscription.
     *
     * @return {@code true} if both stream and consumer are non-empty, {@code false} otherwise
     */
    public boolean isDurableConsumer() {
        return stream != null && !stream.isEmpty() &&
               consumer != null && !consumer.isEmpty();
    }
}
