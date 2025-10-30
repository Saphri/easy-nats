package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsConnection;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for try-with-resources support in NatsConnection.
 * <p>
 * Tests that NatsConnection:
 * 1. Can be used in try-with-resources blocks safely
 * 2. close() method is a no-op and doesn't close underlying connection
 * 3. Connection remains usable after try-with-resources block exits
 * </p>
 */
@QuarkusTest
@DisplayName("NatsConnection Try-with-Resources Integration Tests")
class TryWithResourcesTest {

    @Test
    @DisplayName("NatsConnection can be used in try-with-resources block without side effects")
    void testTryWithResourcesNoSideEffects() throws Exception {
        // When - Use connection in try-with-resources block
        try (NatsConnection conn = NatsTestUtils.getNatsConnection()) {
            // Verify connection is usable inside try block
            assertThat(conn.isClosed()).isFalse();

            // Publish a message
            byte[] data = "try-with-resources test".getBytes();
            conn.publish("test.try-with-resources", data);
        } catch (Exception e) {
            throw new AssertionError("close() should not throw exception", e);
        }

        // Then - Connection should still be active after try block exits
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
    @DisplayName("Connection can be used after try-with-resources block exits")
    void testConnectionUsableAfterTryWithResources() throws Exception {
        // When - Use connection in try-with-resources, then use it again outside
        String message1 = "before try block";
        String subject = "test.try-with-resources.reuse";

        // Publish before try block
        given()
                .contentType(ContentType.TEXT)
                .body(message1)
                .when()
                .post("/connection/publish/" + subject)
                .then()
                .statusCode(200);

        // Use in try-with-resources
        try (NatsConnection conn = NatsTestUtils.getNatsConnection()) {
            assertThat(conn.isClosed()).isFalse();
        }

        // Publish after try block - should still work
        String message2 = "after try block";
        given()
                .contentType(ContentType.TEXT)
                .body(message2)
                .when()
                .post("/connection/publish/" + subject)
                .then()
                .statusCode(200);

        // Then - Verify connection is still active
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
    @DisplayName("Exception in try block doesn't affect connection after close()")
    void testConnectionRecoveryAfterExceptionInTryBlock() throws Exception {
        // When - Exception occurs in try block, close() called, then use connection again
        String testSubject = "test.try-with-resources.recovery";

        // First publish before exception scenario
        given()
                .contentType(ContentType.TEXT)
                .body("before exception")
                .when()
                .post("/connection/publish/" + testSubject)
                .then()
                .statusCode(200);

        // Simulate try-with-resources with exception
        boolean exceptionOccurred = false;
        try (NatsConnection conn = NatsTestUtils.getNatsConnection()) {
            // Verify connection is usable
            assertThat(conn.isClosed()).isFalse();

            // Simulate throwing an exception
            if (true) {
                exceptionOccurred = true;
                throw new RuntimeException("Simulated error");
            }
        } catch (RuntimeException e) {
            // Expected: exception from try block
            assertThat(e.getMessage()).isEqualTo("Simulated error");
        }

        assertThat(exceptionOccurred).isTrue();

        // Then - Connection should still be usable after exception and close()
        var status = given()
                .when()
                .get("/connection/status")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        assertThat(status.getBoolean("active")).isTrue();

        // And - We should be able to publish another message
        given()
                .contentType(ContentType.TEXT)
                .body("after exception recovery")
                .when()
                .post("/connection/publish/" + testSubject)
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Multiple try-with-resources blocks in sequence use same connection instance")
    void testMultipleTryWithResourcesBlocks() throws Exception {
        // When - Use try-with-resources multiple times
        String connectedUrl1 = given()
                .when()
                .get("/connection/info")
                .then()
                .statusCode(200)
                .extract()
                .path("connectedUrl");

        // First try-with-resources
        try (NatsConnection conn1 = NatsTestUtils.getNatsConnection()) {
            assertThat(conn1.isClosed()).isFalse();
            given()
                    .contentType(ContentType.TEXT)
                    .body("message 1")
                    .when()
                    .post("/connection/publish/test.try-with-resources.sequence")
                    .then()
                    .statusCode(200);
        }

        // Second try-with-resources
        try (NatsConnection conn2 = NatsTestUtils.getNatsConnection()) {
            assertThat(conn2.isClosed()).isFalse();
            given()
                    .contentType(ContentType.TEXT)
                    .body("message 2")
                    .when()
                    .post("/connection/publish/test.try-with-resources.sequence")
                    .then()
                    .statusCode(200);
        }

        // Then - Connection should still be same and active
        String connectedUrl2 = given()
                .when()
                .get("/connection/info")
                .then()
                .statusCode(200)
                .extract()
                .path("connectedUrl");

        assertThat(connectedUrl2).isEqualTo(connectedUrl1);

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
}
