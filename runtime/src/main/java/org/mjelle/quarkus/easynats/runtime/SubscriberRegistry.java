package org.mjelle.quarkus.easynats.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

/**
 * Registry of all subscriber methods discovered at build time.
 *
 * <p>
 * This singleton bean holds the metadata for all subscriber methods annotated with
 * {@code @NatsSubscriber}. It is used by the {@code SubscriberInitializer} to create
 * ephemeral consumers and register message handlers at application startup.
 * </p>
 */
@ApplicationScoped
public class SubscriberRegistry {

    private final List<SubscriberMetadata> subscribers = new ArrayList<>();

    /**
     * Registers a subscriber.
     *
     * @param metadata the subscriber metadata
     */
    public void register(SubscriberMetadata metadata) {
        subscribers.add(metadata);
    }

    /**
     * Gets all registered subscribers.
     *
     * @return an immutable copy of the subscribers list
     */
    public List<SubscriberMetadata> getSubscribers() {
        return List.copyOf(subscribers);
    }
}
