package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for tests when NATS Dev Services are disabled.
 *
 * This profile tests two scenarios:
 * 1. Explicitly configured NATS server (dev services should not start)
 * 2. Dev Services disabled via configuration
 */
public class NatsDevServicesDisabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        // Keep the explicitly configured server so Dev Services will NOT start
        config.put("quarkus.easynats.servers", "nats://localhost:4222");
        config.put("quarkus.easynats.username", "guest");
        config.put("quarkus.easynats.password", "guest");
        return config;
    }

    @Override
    public String getConfigProfile() {
        return "test-explicit-nats";
    }
}
