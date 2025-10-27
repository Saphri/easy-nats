package org.mjelle.quarkus.easynats.runtime.consumer;

import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;

import java.util.List;

import org.jboss.logging.Logger;

/**
 * Factory for creating ephemeral NATS JetStream consumer configurations.
 *
 * <p>
 * Ephemeral consumers are temporary consumers that are automatically cleaned up when they become
 * inactive. This factory creates consumer configurations that let the NATS server assign a unique
 * consumer name, ensuring the consumer is truly ephemeral and does not conflict with other
 * instances.
 * </p>
 */
public final class EphemeralConsumerFactory {

    private static final Logger LOGGER = Logger.getLogger(EphemeralConsumerFactory.class);

    private EphemeralConsumerFactory() {
        // Utility class
    }

    /**
     * Creates an ephemeral consumer configuration.
     *
     * <p>
     * The configuration will:
     * </p>
     * <ul>
     * <li>Not specify a consumer name (let the server assign one)</li>
     * <li>Deliver new messages (starting from now, not historical messages)</li>
     * <li>Be eligible for automatic cleanup when inactive</li>
     * </ul>
     *
     * @return a consumer configuration for ephemeral consumption
     */
    public static ConsumerConfiguration createEphemeralConsumerConfig(String subject) {
        LOGGER.debug("Creating ephemeral consumer configuration");
        return ConsumerConfiguration.builder()
                .deliverPolicy(DeliverPolicy.New)
                .filterSubjects(List.of(subject))
                .build();
    }
}
