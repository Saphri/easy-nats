package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Test resource that ensures the NATS JetStream stream exists before tests run.
 *
 * This resource handles both Dev Services and explicit NATS configuration.
 * For Dev Services tests, stream initialization is deferred until Quarkus starts
 * and the configuration is injected.
 */
public class NatsStreamTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        // Try to initialize streams if explicitly configured NATS is available
        // For Dev Services tests, this will fail gracefully and let the application initialize streams
        String natsServersEnv = System.getenv("QUARKUS_EASYNATS_SERVERS");
        if (natsServersEnv != null && !natsServersEnv.isEmpty()) {
            // Explicit NATS configuration - try to initialize streams
            try {
                System.out.println("NatsStreamTestResource: Initializing streams with explicit NATS configuration");
                NatsTestUtils.getConnection();
                System.out.println("NatsStreamTestResource: Streams initialized successfully");
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize NATS stream for tests with explicit config", e);
            }
        } else {
            // Dev Services will provide NATS - defer stream initialization until after Quarkus starts
            System.out.println("NatsStreamTestResource: Skipping stream initialization (Dev Services will provide NATS)");
        }
        return Map.of();
    }

    @Override
    public void stop() {
        // Stream cleanup handled by NatsTestUtils if needed
    }
}
