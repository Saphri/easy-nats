package org.mjelle.quarkus.easynats.deployment.build;

import io.quarkus.builder.item.MultiBuildItem;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

/**
 * Build item for passing subscriber metadata through the Quarkus build pipeline.
 *
 * <p>
 * This build item carries the metadata for a subscriber method discovered at build time. It is
 * produced by the {@code SubscriberDiscoveryProcessor} and consumed by other build steps to
 * register the subscriber with the runtime.
 * </p>
 */
public final class SubscriberBuildItem extends MultiBuildItem {

    private final SubscriberMetadata metadata;

    /**
     * Creates a new subscriber build item.
     *
     * @param metadata the subscriber metadata
     */
    public SubscriberBuildItem(SubscriberMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Gets the subscriber metadata.
     *
     * @return the metadata
     */
    public SubscriberMetadata getMetadata() {
        return metadata;
    }
}
