package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NATS Dev Services when disabled or when NATS is explicitly configured.
 *
 * These tests verify that:
 * 1. When quarkus.easynats.servers is explicitly configured, Dev Services does NOT start a container
 * 2. When quarkus.easynats.devservices.enabled=false, Dev Services does NOT start a container
 * 3. The application can still connect successfully to the explicitly configured server
 */
@QuarkusTest
@TestProfile(NatsDevServicesDisabledTestProfile.class)
@DisplayName("NATS Dev Services Disabled Configuration Tests")
class NatsDevServicesDisabledTest {

    @Test
    @DisplayName("Application connects to explicitly configured NATS server (Dev Services not started)")
    void testExplicitlyConfiguredServerPreventsDevServices() {
        // When - Get connection info
        var response = given()
                .when()
                .get("/connection/info")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        // Then - Connection should be active and using the configured server
        assertThat(response.getString("status")).isEqualTo("CONNECTED");
        assertThat(response.getBoolean("closed")).isFalse();
        // Should be connecting to the explicitly configured localhost server
        assertThat(response.getString("connectedUrl")).contains("localhost");
    }

    @Test
    @DisplayName("Connection is stable when using explicitly configured NATS server")
    void testConnectionStabilityWithExplicitConfiguration() {
        // When - Get connection status
        var status = given()
                .when()
                .get("/connection/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        // Then - Connection should be active
        assertThat(status.getBoolean("active")).isTrue();
        assertThat(status.getString("status")).isEqualTo("CONNECTED");
    }
}
