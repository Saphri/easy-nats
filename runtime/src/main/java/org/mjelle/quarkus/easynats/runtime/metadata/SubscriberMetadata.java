package org.mjelle.quarkus.easynats.runtime.metadata;

/**
 * Runtime metadata for a subscriber method.
 *
 * <p>
 * This record holds information about a method annotated with {@code @NatsSubscriber} that is
 * discovered at build time by the Jandex scanner and passed to runtime.
 * </p>
 *
 * @param subject the NATS subject to subscribe to
 * @param methodClass the fully qualified class name containing the subscriber method
 * @param methodName the name of the subscriber method
 * @param declaringBeanClass the fully qualified class name of the CDI bean
 */
public record SubscriberMetadata(
        String subject, String methodClass, String methodName, String declaringBeanClass) {}
