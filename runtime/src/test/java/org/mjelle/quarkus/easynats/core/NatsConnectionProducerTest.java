package org.mjelle.quarkus.easynats.core;

import io.nats.client.Options;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsConfigurationException;
import org.mjelle.quarkus.easynats.runtime.NatsConfiguration;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for NatsConnectionProducer.
 * <p>
 * Tests the @DefaultBean CDI producer that creates Options from NatsConfiguration properties.
 * Validates that:
 * - Options are created correctly from valid configuration
 * - Required properties are validated
 * - Custom Options beans can override the default producer
 */
@DisplayName("NATS Connection Producer Tests")
class NatsConnectionProducerTest {

    private NatsConnectionProducer producer;
    private TlsConfigurationRegistry tlsRegistry;
    private ConnectionStatusHolder statusHolder;

    @BeforeEach
    void setUp() {
        // Create mock/stub dependencies
        tlsRegistry = createMockTlsRegistry();
        statusHolder = new ConnectionStatusHolder();

        // Create the producer with test dependencies
        producer = new NatsConnectionProducer(
                tlsRegistry,
                Executors.newFixedThreadPool(1),  // Simple executor for testing
                statusHolder
        );
    }

    /**
     * Creates a mock TlsConfigurationRegistry that always returns empty (no TLS config).
     * This is sufficient for tests that don't test TLS configuration.
     */
    private TlsConfigurationRegistry createMockTlsRegistry() {
        return new TlsConfigurationRegistry() {
            @Override
            public Optional<TlsConfiguration> get(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<TlsConfiguration> getDefault() {
                return Optional.empty();
            }

            @Override
            public void register(String name, TlsConfiguration configuration) {
                // No-op for testing
            }
        };
    }

    // ========== Helper: Create Mock NatsConfiguration ==========

    /**
     * Creates a mock NatsConfiguration for testing.
     */
    private static class TestNatsConfiguration implements NatsConfiguration {
        private final Optional<List<String>> servers;
        private final Optional<String> username;
        private final Optional<String> password;
        private final boolean sslEnabled;
        private final Optional<String> tlsConfigurationName;
        private final boolean logPayloadsOnError;

        TestNatsConfiguration(
                Optional<List<String>> servers,
                Optional<String> username,
                Optional<String> password,
                boolean sslEnabled) {
            this.servers = servers;
            this.username = username;
            this.password = password;
            this.sslEnabled = sslEnabled;
            this.tlsConfigurationName = Optional.empty();
            this.logPayloadsOnError = true;
        }

        @Override
        public Optional<List<String>> servers() {
            return servers;
        }

        @Override
        public Optional<String> username() {
            return username;
        }

        @Override
        public Optional<String> password() {
            return password;
        }

        @Override
        public Optional<String> tlsConfigurationName() {
            return tlsConfigurationName;
        }

        @Override
        public boolean sslEnabled() {
            return sslEnabled;
        }

        @Override
        public boolean logPayloadsOnError() {
            return logPayloadsOnError;
        }
    }

    // ========== Test Cases: Valid Configuration ==========

    @Test
    @DisplayName("Producer creates Options from valid configuration with servers only")
    void testProducerCreatesValidOptionsFromServersOnly() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.empty(),
                Optional.empty(),
                false
        );

        // When
        Options options = producer.natsOptions(config);

        // Then
        assertThat(options).isNotNull();
    }

    @Test
    @DisplayName("Producer creates Options with username and password")
    void testProducerCreatesValidOptionsWithAuthentication() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.of("admin"),
                Optional.of("secret"),
                false
        );

        // When
        Options options = producer.natsOptions(config);

        // Then
        assertThat(options).isNotNull();
    }

    @Test
    @DisplayName("Producer creates Options with multiple servers")
    void testProducerCreatesValidOptionsWithMultipleServers() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://server1:4222", "nats://server2:4222", "nats://server3:4222")),
                Optional.of("user"),
                Optional.of("pass"),
                false
        );

        // When
        Options options = producer.natsOptions(config);

        // Then
        assertThat(options).isNotNull();
    }

    @Test
    @DisplayName("Producer creates Options with SSL/TLS enabled")
    void testProducerCreatesValidOptionsWithSSL() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("tls://localhost:4222")),
                Optional.of("user"),
                Optional.of("pass"),
                true  // SSL enabled
        );

        // When
        Options options = producer.natsOptions(config);

        // Then
        assertThat(options).isNotNull();
    }

    // ========== Test Cases: Validation - Servers ==========

    @Test
    @DisplayName("Producer throws exception when servers property is missing")
    void testProducerThrowsExceptionWhenServersPropertyIsMissing() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.empty(),  // servers missing
                Optional.of("user"),
                Optional.of("pass"),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("quarkus.easynats.servers");
    }

    @Test
    @DisplayName("Producer throws exception when servers list is empty")
    void testProducerThrowsExceptionWhenServersListIsEmpty() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of()),  // empty list
                Optional.of("user"),
                Optional.of("pass"),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    @DisplayName("Producer throws exception when a server URL is empty string")
    void testProducerThrowsExceptionWhenServerUrlIsEmptyString() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://valid:4222", "", "nats://another:4222")),
                Optional.empty(),
                Optional.empty(),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("Producer throws exception when a server URL is null")
    void testProducerThrowsExceptionWhenServerUrlIsNull() {
        // Given - use ArrayList since List.of() doesn't allow nulls
        java.util.ArrayList<String> servers = new java.util.ArrayList<>();
        servers.add("nats://valid:4222");
        servers.add(null);
        servers.add("nats://another:4222");

        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(servers),
                Optional.empty(),
                Optional.empty(),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("cannot be empty or null");
    }

    // ========== Test Cases: Validation - Authentication ==========

    @Test
    @DisplayName("Producer throws exception when username is present but password is missing")
    void testProducerThrowsExceptionWhenUsernameWithoutPassword() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.of("admin"),
                Optional.empty(),  // password missing
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("username")
                .hasMessageContaining("password");
    }

    @Test
    @DisplayName("Producer throws exception when password is present but username is missing")
    void testProducerThrowsExceptionWhenPasswordWithoutUsername() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.empty(),  // username missing
                Optional.of("secret"),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("password")
                .hasMessageContaining("username");
    }

    @Test
    @DisplayName("Producer throws exception when username is empty string")
    void testProducerThrowsExceptionWhenUsernameIsEmpty() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.of(""),     // empty username
                Optional.of("pass"),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class);
    }

    @Test
    @DisplayName("Producer throws exception when password is empty string")
    void testProducerThrowsExceptionWhenPasswordIsEmpty() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.of("user"),
                Optional.of(""),     // empty password
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class);
    }

    // ========== Test Cases: Error Messages ==========

    @Test
    @DisplayName("Producer provides clear error message when servers are missing")
    void testProducerErrorMessageIsClearForMissingServers() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.empty(),
                Optional.of("user"),
                Optional.of("pass"),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContainingAll(
                        "quarkus.easynats.servers",
                        "required",
                        "application.properties",
                        "custom"
                );
    }

    @Test
    @DisplayName("Producer provides clear error message for username/password mismatch")
    void testProducerErrorMessageIsClearForAuthMismatch() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                Optional.of(List.of("nats://localhost:4222")),
                Optional.of("admin"),
                Optional.empty(),
                false
        );

        // When & Then
        assertThatThrownBy(() -> producer.natsOptions(config))
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContainingAll(
                        "username",
                        "password",
                        "both"
                );
    }
}
