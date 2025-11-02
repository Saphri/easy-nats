package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Test resource for integration tests.
 *
 * Stream and consumer creation is now handled by nats.conf.
 * This resource simply validates the connection can be established.
 */
public class NatsStreamTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        // Stream and consumer creation is now handled by nats.conf
        // No initialization needed here
        return Map.of();
    }

    @Override
    public void stop() {
        // Stream cleanup handled by NatsTestUtils if needed
    }
}
