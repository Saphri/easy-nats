package org.mjelle.quarkus.easynats.deployment.devservices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ContainerConfig record.
 * Tests URL generation, configuration mapping, and validation.
 */
@DisplayName("ContainerConfig Record Tests")
class ContainerConfigTest {

    @Test
    @DisplayName("toConnectionUrl generates correct nats:// URL for single container")
    void testToConnectionUrlSingleContainer() {
        ContainerConfig config = new ContainerConfig(
            "abc123def456",
            "localhost",
            "4222",
            "nats",
            "nats",
            false
        );

        String url = config.toConnectionUrl();
        assertThat(url).isEqualTo("nats://localhost:4222");
    }

    @Test
    @DisplayName("toConnectionUrl generates correct tls:// URL when SSL enabled")
    void testToConnectionUrlWithTls() {
        ContainerConfig config = new ContainerConfig(
            "abc123def456",
            "localhost",
            "4222",
            "secure-user",
            "secure-pass",
            true
        );

        String url = config.toConnectionUrl();
        assertThat(url).isEqualTo("tls://localhost:4222");
    }

    @Test
    @DisplayName("toConnectionUrl generates comma-separated URLs for clustering")
    void testToConnectionUrlClustering() {
        ContainerConfig config = new ContainerConfig(
            "abc123,def456,ghi789",
            "localhost,localhost,localhost",
            "4222,4223,4224",
            "nats",
            "nats",
            false
        );

        String url = config.toConnectionUrl();
        assertThat(url).isEqualTo("nats://localhost:4222,nats://localhost:4223,nats://localhost:4224");
    }

    @Test
    @DisplayName("toConfigurationMap returns correct property mappings")
    void testToConfigurationMap() {
        ContainerConfig config = new ContainerConfig(
            "abc123def456",
            "localhost",
            "4222",
            "admin",
            "secretpass",
            false
        );

        Map<String, String> map = config.toConfigurationMap();

        assertThat(map).containsEntry("quarkus.easynats.servers", "nats://localhost:4222")
            .containsEntry("quarkus.easynats.username", "admin")
            .containsEntry("quarkus.easynats.password", "secretpass")
            .containsEntry("quarkus.easynats.ssl-enabled", "false");
    }

    @Test
    @DisplayName("toConfigurationMap includes tls:// scheme when SSL enabled")
    void testToConfigurationMapWithTls() {
        ContainerConfig config = new ContainerConfig(
            "abc123def456",
            "localhost",
            "4222",
            "user",
            "pass",
            true
        );

        Map<String, String> map = config.toConfigurationMap();

        assertThat(map.get("quarkus.easynats.servers")).isEqualTo("tls://localhost:4222");
        assertThat(map.get("quarkus.easynats.ssl-enabled")).isEqualTo("true");
    }

    @Test
    @DisplayName("Constructor validates host is not empty")
    void testValidationHostEmpty() {
        assertThatThrownBy(() -> new ContainerConfig(
            "abc123",
            "",
            "4222",
            "nats",
            "nats",
            false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host cannot be null or empty");
    }

    @Test
    @DisplayName("Constructor validates port is numeric")
    void testValidationPortNonNumeric() {
        assertThatThrownBy(() -> new ContainerConfig(
            "abc123",
            "localhost",
            "invalid",
            "nats",
            "nats",
            false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port must be numeric");
    }

    @Test
    @DisplayName("Constructor validates port is within valid range")
    void testValidationPortRange() {
        assertThatThrownBy(() -> new ContainerConfig(
            "abc123",
            "localhost",
            "70000",
            "nats",
            "nats",
            false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port must be 1-65535");
    }

    @Test
    @DisplayName("Constructor validates username is not empty")
    void testValidationUsernameEmpty() {
        assertThatThrownBy(() -> new ContainerConfig(
            "abc123",
            "localhost",
            "4222",
            "",
            "nats",
            false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username cannot be null or empty");
    }

    @Test
    @DisplayName("Constructor validates password is not null")
    void testValidationPasswordNull() {
        assertThatThrownBy(() -> new ContainerConfig(
            "abc123",
            "localhost",
            "4222",
            "nats",
            null,
            false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("password cannot be null");
    }

    @Test
    @DisplayName("Constructor validates containerId is not empty")
    void testValidationContainerIdEmpty() {
        assertThatThrownBy(() -> new ContainerConfig(
            "",
            "localhost",
            "4222",
            "nats",
            "nats",
            false
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("containerId cannot be null or empty");
    }
}
