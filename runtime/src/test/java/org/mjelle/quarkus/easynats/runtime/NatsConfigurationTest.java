package org.mjelle.quarkus.easynats.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsConfigurationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for NatsConfiguration.
 * Tests configuration validation rules.
 */
@DisplayName("NatsConfiguration Unit Tests")
class NatsConfigurationTest {

    @Test
    @DisplayName("Valid configuration with servers only should pass validation")
    void testValidConfigWithServersOnly() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then - no exception should be thrown
        config.validate();
    }

    @Test
    @DisplayName("Valid configuration with multiple servers should pass validation")
    void testValidConfigWithMultipleServers() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://server1:4222", "nats://server2:4222", "nats://server3:4222"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then - no exception should be thrown
        config.validate();
    }

    @Test
    @DisplayName("Valid configuration with username and password should pass validation")
    void testValidConfigWithUsernameAndPassword() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222"),
                Optional.of("testuser"),
                Optional.of("testpass"),
                Optional.empty()
        );

        // When/Then - no exception should be thrown
        config.validate();
    }

    @Test
    @DisplayName("Valid configuration with TLS configuration name should pass validation")
    void testValidConfigWithTlsConfigurationName() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("tls://localhost:4222"),
                Optional.of("testuser"),
                Optional.of("testpass"),
                Optional.of("my-nats-tls")
        );

        // When/Then - no exception should be thrown
        config.validate();
    }

    @Test
    @DisplayName("Configuration with null servers should fail validation")
    void testConfigWithNullServers() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("At least one NATS server must be configured");
    }

    @Test
    @DisplayName("Configuration with empty servers list should fail validation")
    void testConfigWithEmptyServersList() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("At least one NATS server must be configured");
    }

    @Test
    @DisplayName("Configuration with empty server URL should fail validation")
    void testConfigWithEmptyServerUrl() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222", "", "nats://server2:4222"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("NATS server URL cannot be empty");
    }


    @Test
    @DisplayName("Configuration with username but no password should fail validation")
    void testConfigWithUsernameButNoPassword() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222"),
                Optional.of("testuser"),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("Username specified but password is missing");
    }

    @Test
    @DisplayName("Configuration with password but no username should fail validation")
    void testConfigWithPasswordButNoUsername() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222"),
                Optional.empty(),
                Optional.of("testpass"),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("Password specified but username is missing");
    }

    @Test
    @DisplayName("Configuration with empty username but valid password should fail validation")
    void testConfigWithEmptyUsernameButValidPassword() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222"),
                Optional.of(""),
                Optional.of("testpass"),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("Password specified but username is missing");
    }

    @Test
    @DisplayName("Configuration with valid username but empty password should fail validation")
    void testConfigWithValidUsernameButEmptyPassword() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222"),
                Optional.of("testuser"),
                Optional.of(""),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("Username specified but password is missing");
    }

    @Test
    @DisplayName("Configuration with whitespace-only server URL should fail validation")
    void testConfigWithWhitespaceOnlyServerUrl() {
        // Given
        NatsConfiguration config = new TestNatsConfiguration(
                List.of("nats://localhost:4222", "   ", "nats://server2:4222"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When/Then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NatsConfigurationException.class)
                .hasMessageContaining("NATS server URL cannot be empty");
    }

    /**
     * Test implementation of NatsConfiguration interface for unit testing.
     */
    private static class TestNatsConfiguration implements NatsConfiguration {
        private final List<String> servers;
        private final Optional<String> username;
        private final Optional<String> password;
        private final Optional<String> tlsConfigurationName;

        TestNatsConfiguration(List<String> servers, Optional<String> username, Optional<String> password, Optional<String> tlsConfigurationName) {
            this.servers = servers;
            this.username = username;
            this.password = password;
            this.tlsConfigurationName = tlsConfigurationName;
        }

        @Override
        public List<String> servers() {
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
    }
}
