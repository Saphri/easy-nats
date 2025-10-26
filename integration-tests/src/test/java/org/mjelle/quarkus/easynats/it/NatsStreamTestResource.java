package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Test resource that ensures the NATS JetStream stream exists before tests run.
 *
 * This is critical because the SubscriberInitializer runs during Quarkus startup
 * and requires the stream to already exist.
 */
public class NatsStreamTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        try {
            // This will create the stream if it doesn't exist
            NatsTestUtils.getConnection();
            System.out.println("NatsStreamTestResource: Stream initialized successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NATS stream for tests", e);
        }
        return Map.of();
    }

    @Override
    public void stop() {
        // Stream cleanup handled by NatsTestUtils if needed
    }
}
