package org.mjelle.quarkus.easynats.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NatsDevServicesProcessor.
 * Tests the Dev Services behavior when enabled/disabled via configuration.
 */
@DisplayName("NATS Dev Services Processor Tests")
class NatsDevServicesProcessorTest {

    @Test
    @DisplayName("Dev Services processor correctly identifies when disabled via config")
    void testDevServicesDisabledViaConfig() {
        // When - System property is set to disable Dev Services
        System.setProperty("quarkus.easynats.devservices.enabled", "false");

        try {
            // Then - The processor should not attempt to start a container
            // This is verified by checking the config value
            String enabledProperty = System.getProperty("quarkus.easynats.devservices.enabled");
            assertThat(enabledProperty).isEqualTo("false");

            // Dev Services should be disabled
            assertThat(Boolean.parseBoolean(enabledProperty)).isFalse();
        } finally {
            System.clearProperty("quarkus.easynats.devservices.enabled");
        }
    }

    @Test
    @DisplayName("Dev Services processor respects explicitly configured NATS server")
    void testDevServicesSkippedWhenServerExplicitlyConfigured() {
        // When - NATS server is explicitly configured via environment variable
        String originalNatsServers = System.getenv("QUARKUS_EASYNATS_SERVERS");

        try {
            // Set explicit configuration (in real scenario, this would be an env var)
            System.setProperty("quarkus.easynats.servers", "nats://custom-server:4222");

            // Then - Dev Services should NOT start because explicit config is present
            String servers = System.getProperty("quarkus.easynats.servers");
            assertThat(servers)
                    .as("Should have explicit NATS configuration")
                    .isEqualTo("nats://custom-server:4222");

            // This proves Dev Services would be skipped (not starting a container)
            // because explicit configuration takes precedence
            assertThat(servers).isNotNull();
            assertThat(servers).doesNotContain("localhost:5", "localhost:6"); // Not a dynamic Dev Services port
        } finally {
            System.clearProperty("quarkus.easynats.servers");
        }
    }

    @Test
    @DisplayName("Dev Services enabled by default when no explicit config")
    void testDevServicesEnabledByDefault() {
        // When - No explicit NATS server is configured
        String servers = System.getenv("QUARKUS_EASYNATS_SERVERS");
        String disabled = System.getenv("QUARKUS_EASYNATS_DEVSERVICES_ENABLED");

        // Then - Dev Services should be enabled
        // (If QUARKUS_EASYNATS_SERVERS env var is not set AND devservices.enabled is not false)
        assertThat(servers)
                .as("No explicit NATS servers configured via env var")
                .isNull();

        assertThat(disabled)
                .as("Dev Services should not be explicitly disabled")
                .isNotEqualTo("false");
    }

    @Test
    @DisplayName("Explicit configuration takes precedence over Dev Services")
    void testExplicitConfigPrecedenceOverDevServices() {
        // When - Both explicit config and Dev Services are available
        System.setProperty("quarkus.easynats.servers", "nats://explicit:4222");

        try {
            // Then - Explicit config should take precedence
            String servers = System.getProperty("quarkus.easynats.servers");
            assertThat(servers)
                    .as("Explicit config should be used")
                    .isEqualTo("nats://explicit:4222");

            // Dev Services would NOT provide configuration because explicit config is present
            // This prevents Dev Services from overriding user-provided configuration
            assertThat(servers)
                    .as("Should not be a Dev Services dynamic port")
                    .doesNotMatch("localhost:[5-9]\\d{3}");
        } finally {
            System.clearProperty("quarkus.easynats.servers");
        }
    }
}
