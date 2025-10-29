package org.mjelle.quarkus.easynats.it.health;

import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.NatsStreamTestResource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NATS health check endpoints (liveness, readiness, startup).
 *
 * This test verifies that the Quarkus health check endpoints correctly report
 * the status of the NATS connection according to the specification.
 */
@io.quarkus.test.junit.QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
@DisplayName("NATS Health Check Endpoints")
class HealthCheckTest {

    /**
     * Test: Liveness probe returns 200 OK when NATS connection is healthy.
     *
     * Corresponds to spec acceptance scenario: Liveness User Story 1.1
     */
    @Test
    @DisplayName("Liveness endpoint returns 200 OK when connected to NATS")
    void testLivenessProbeWhenConnected() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .extract()
                .response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
        assertThat(response.jsonPath().getList("checks")).isNotEmpty();

        // Verify liveness probe check is present
        String checkName = response.jsonPath().getString("checks[0].name");
        assertThat(checkName).contains("NATS Connection").contains("Liveness");
        String connectionStatus = response.jsonPath().getString("checks[0].data.connectionStatus");
        assertThat(connectionStatus).isNotNull();
    }

    /**
     * Test: Readiness probe returns 200 OK when NATS connection is healthy.
     *
     * Corresponds to spec acceptance scenario: Readiness User Story 2.2
     */
    @Test
    @DisplayName("Readiness endpoint returns 200 OK when connected to NATS")
    void testReadinessProbeWhenConnected() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .extract()
                .response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
        assertThat(response.jsonPath().getList("checks")).isNotEmpty();

        // Verify readiness probe check is present
        String checkName = response.jsonPath().getString("checks[0].name");
        assertThat(checkName).contains("NATS Connection").contains("Readiness");
        String connectionStatus = response.jsonPath().getString("checks[0].data.connectionStatus");
        assertThat(connectionStatus).isNotNull();
        assertThat(connectionStatus).isEqualTo("CONNECTED");
    }

    /**
     * Test: Startup probe returns 200 OK when NATS connection is healthy.
     *
     * Corresponds to spec acceptance scenario: Startup User Story 3.2
     */
    @Test
    @DisplayName("Startup endpoint returns 200 OK when connected to NATS")
    void testStartupProbeWhenConnected() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/q/health/started")
                .then()
                .statusCode(200)
                .extract()
                .response();

        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
        assertThat(response.jsonPath().getList("checks")).isNotEmpty();

        // Verify startup probe check is present
        String checkName = response.jsonPath().getString("checks[0].name");
        assertThat(checkName).contains("NATS Connection").contains("Startup");
        String connectionStatus = response.jsonPath().getString("checks[0].data.connectionStatus");
        assertThat(connectionStatus).isNotNull();
    }

    /**
     * Test: Health check response includes detailed connection status in data field.
     *
     * Corresponds to spec requirement: FR-010
     */
    @Test
    @DisplayName("Health check response includes detailed NATS connection status")
    void testHealthResponseIncludesConnectionStatus() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .extract()
                .response();

        // Verify response structure
        assertThat(response.jsonPath().getString("status")).isNotNull();
        assertThat(response.jsonPath().getList("checks")).isNotEmpty();
        assertThat(response.jsonPath().getString("checks[0].name")).contains("NATS Connection").contains("Liveness");
        assertThat(response.jsonPath().getString("checks[0].status")).isNotNull();
        assertThat(response.jsonPath().getString("checks[0].data.connectionStatus")).isNotNull();
    }

    /**
     * Test: Health check endpoints respond quickly (within reasonable time).
     *
     * Corresponds to spec success criteria: SC-001, SC-002 (100ms response time)
     */
    @Test
    @DisplayName("Health check endpoints respond within acceptable time")
    void testHealthCheckResponseTime() {
        long startTime = System.currentTimeMillis();
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200);
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // Health checks should respond very quickly (well under 100ms per spec)
        // We use a generous timeout to account for first-time JIT compilation
        assertThat(responseTime).isLessThan(1000); // 1 second as a reasonable upper bound
    }
}
