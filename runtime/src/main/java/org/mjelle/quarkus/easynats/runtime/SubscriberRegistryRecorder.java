package org.mjelle.quarkus.easynats.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import java.util.List;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

/**
 * Runtime recorder for populating the {@link SubscriberRegistry}.
 *
 * <p>
 * This recorder is called at build time by the {@code QuarkusEasyNatsProcessor} to wire up
 * subscriber metadata discovered at build time into the runtime registry.
 * </p>
 */
@Recorder
public class SubscriberRegistryRecorder {

    /**
     * Registers subscriber metadata with the registry.
     * <p>
     * The registry is looked up from the Arc container at runtime.
     *
     * @param subscribers the list of discovered subscribers
     */
    public void registerSubscribers(List<SubscriberMetadata> subscribers) {
        SubscriberRegistry registry = Arc.container().instance(SubscriberRegistry.class).get();
        for (SubscriberMetadata metadata : subscribers) {
            registry.register(metadata);
        }
    }
}
