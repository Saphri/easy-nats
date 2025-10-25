package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Integration test for NatsPublisher.
 * Tests the publisher functionality against a real NATS broker via docker-compose
 * using RestAssured to call REST endpoints (no @Inject in integration tests).
 */
@QuarkusIntegrationTest
public class BasicPublisherIT {

    /**
     * Test that the publish endpoint responds successfully.
     * Verifies the REST endpoint is available and accepts publish requests.
     */
    @Test
    public void testPublishEndpointSucceeds() {
        given()
                .contentType("text/plain")
                .body("hello")
                .when().post("/quarkus-easy-nats/publish")
                .then()
                .statusCode(200)
                .body(containsString("Published: hello"));
    }

    /**
     * Test that multiple messages can be published.
     * Verifies the publisher can handle sequential publish calls.
     */
    @Test
    public void testMultiplePublishesSucceed() {
        given()
                .contentType("text/plain")
                .body("message-1")
                .when().post("/quarkus-easy-nats/publish")
                .then()
                .statusCode(200);

        given()
                .contentType("text/plain")
                .body("message-2")
                .when().post("/quarkus-easy-nats/publish")
                .then()
                .statusCode(200);
    }

    /**
     * Test that messages with timestamps are published.
     * Verifies the publisher sends messages to NATS broker.
     */
    @Test
    public void testMessageAppearsOnBroker() {
        String testMessage = "test-message-" + System.currentTimeMillis();
        given()
                .contentType("text/plain")
                .body(testMessage)
                .when().post("/quarkus-easy-nats/publish")
                .then()
                .statusCode(200)
                .body(containsString("Published: " + testMessage));
    }
}
