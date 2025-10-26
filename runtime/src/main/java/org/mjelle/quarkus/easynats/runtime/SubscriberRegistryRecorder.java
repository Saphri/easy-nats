package org.mjelle.quarkus.easynats.runtime;

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
     *
     * @param registry the subscriber registry instance
     * @param subscribers the list of discovered subscribers
     */
    public void registerSubscribers(SubscriberRegistry registry, List<SubscriberMetadata> subscribers) {
        for (SubscriberMetadata metadata : subscribers) {
            registry.register(metadata);
        }
    }
}
