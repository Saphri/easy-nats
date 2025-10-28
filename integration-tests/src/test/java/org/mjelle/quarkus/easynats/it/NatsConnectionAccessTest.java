package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for NatsConnection access API.
 * <p>
 * Tests that NatsConnection can be injected into user code and used to:
 * 1. Get connection information (server URL, status)
 * 2. Publish messages directly
 * 3. Verify close() is a safe no-op
 * 4. Access the connection from multiple beans
 * </p>
 */
@QuarkusTest
@DisplayName("NatsConnection Access Integration Tests")
class NatsConnectionAccessTest {

    @Test
    @DisplayName("NatsConnection should be injectable and provide connection info")
    void testConnectionInfoAccess() {
        // When - Get connection info via REST endpoint
        var response = given()
                .when()
                .get("/connection/info")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        // Then - Verify connection info is present and valid
        String connectedUrl = response.getString("connectedUrl");
        String status = response.getString("status");
        Boolean closed = response.getBoolean("closed");
        String serverVersion = response.getString("serverVersion");

        assertThat(connectedUrl).isNotNull().contains("localhost:4222");
        assertThat(status).isEqualTo("CONNECTED");
        assertThat(closed).isFalse();
        assertThat(serverVersion).isNotNull();
    }

    @Test
    @DisplayName("NatsConnection should allow publishing messages directly")
    void testDirectPublishing() {
        // Given
        String subject = "test.connection.publish";
        String message = "Hello from NatsConnection";

        // When - Publish message via connection
        given()
                .contentType(ContentType.TEXT)
                .body(message)
                .when()
                .post("/connection/publish/" + subject)
                .then()
                .statusCode(200);

        // Then - Verify message was published successfully
        // (In a real scenario, we would verify the message was received by a subscriber)
    }

    @Test
    @DisplayName("NatsConnection close() should be a no-op and not close underlying connection")
    void testCloseIsNoOp() {
        // Given - Connection is active
        var statusBefore = given()
                .when()
                .get("/connection/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        assertThat(statusBefore.getBoolean("active")).isTrue();

        // When - Call close() via test endpoint
        var closeTestResult = given()
                .when()
                .post("/connection/test-close-noop")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        // Then - Verify close() was a no-op
        assertThat(closeTestResult.getBoolean("wasClosedBefore")).isFalse();
        assertThat(closeTestResult.getBoolean("isClosedAfter")).isFalse();
        assertThat(closeTestResult.getBoolean("closeIsNoOp")).isTrue();

        // And - Connection should still be active
        var statusAfter = given()
                .when()
                .get("/connection/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        assertThat(statusAfter.getBoolean("active")).isTrue();
    }

    @Test
    @DisplayName("NatsConnection should maintain CONNECTED status throughout application lifecycle")
    void testConnectionStability() {
        // When - Get status multiple times
        for (int i = 0; i < 5; i++) {
            var status = given()
                    .when()
                    .get("/connection/status")
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .jsonPath();

            // Then - Status should always be active
            assertThat(status.getString("status")).isEqualTo("CONNECTED");
            assertThat(status.getBoolean("active")).isTrue();
        }
    }

    @Test
    @DisplayName("Multiple publish operations should work without connection issues")
    void testMultiplePublishOperations() {
        // Given
        String subject = "test.connection.multiple";

        // When - Publish multiple messages
        for (int i = 0; i < 10; i++) {
            String message = "Message " + i;

            given()
                    .contentType(ContentType.TEXT)
                    .body(message)
                    .when()
                    .post("/connection/publish/" + subject)
                    .then()
                    .statusCode(200);
        }

        // Then - Connection should still be active
        var status = given()
                .when()
                .get("/connection/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        assertThat(status.getBoolean("active")).isTrue();
    }

    @Test
    @DisplayName("Connection info should be consistent across multiple requests")
    void testConnectionInfoConsistency() {
        // When - Get connection info multiple times
        String firstUrl = given()
                .when()
                .get("/connection/info")
                .then()
                .statusCode(200)
                .extract()
                .path("connectedUrl");

        // Then - All requests should return the same URL (same connection instance)
        for (int i = 0; i < 5; i++) {
            String url = given()
                    .when()
                    .get("/connection/info")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("connectedUrl");

            assertThat(url).isEqualTo(firstUrl);
        }
    }
}
