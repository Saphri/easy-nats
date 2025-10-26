package org.mjelle.quarkus.easynats.it;

import io.nats.client.Nats;
import io.nats.client.Message;
import io.nats.client.JetStream;
import io.nats.client.Options;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for TypedPublisherResource.
 * Tests typed publishing and CloudEvents publishing via REST endpoints.
 */
@QuarkusTest
public class TypedPublisherTest {

    /**
     * Test that String publishing works.
     * Verifies the REST endpoint returns 200 and publishes successfully.
     */
    @Test
    public void testPublishString() {
        given()
            .contentType("application/json")
            .body("\"hello world\"")
            .when()
            .post("/typed-publisher/string")
            .then()
            .statusCode(200)
            .body("status", equalTo("published"));
    }

    /**
     * Test that TestOrder publishing works.
     * Verifies JSON serialization and successful publish.
     */
    @Test
    public void testPublishOrder() {
        TestOrder order = new TestOrder("ORD-123", 99);

        given()
            .contentType("application/json")
            .body(order)
            .when()
            .post("/typed-publisher/order")
            .then()
            .statusCode(200)
            .body("status", equalTo("published"));
    }


    /**
     * Test that published String appears on NATS broker.
     * Uses JetStream subscription to verify message delivery.
     */
    @Test
    public void testPublishedStringAppearsOnBroker() throws Exception {
        var options = new Options.Builder()
            .server("nats://localhost:4222")
            .userInfo("guest", "guest")
            .build();
        var connection = Nats.connect(options);

        try {
            JetStream js = connection.jetStream();
            var subscription = js.subscribe("test");

            // Publish via REST endpoint
            String testMessage = "integration-test-message";
            given()
                .contentType("application/json")
                .body("\"" + testMessage + "\"")
                .when()
                .post("/typed-publisher/string")
                .then()
                .statusCode(200)
                .body("status", equalTo("published"));

            // Wait for message on broker
            await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Message msg = subscription.nextMessage(Duration.ofMillis(500));
                    assertThat(msg).isNotNull();

                    String receivedMessage = new String(msg.getData(), StandardCharsets.UTF_8);
                    assertThat(receivedMessage).isEqualTo(testMessage);

                    msg.ack();
                });
        } finally {
            connection.close();
        }
    }

    /**
     * Test that CloudEvents publishing with auto-generated metadata works.
     * Verifies ceId, ceTime, and ceSource are generated.
     */
    @Test
    public void testPublishStringCloudEvents() {
        given()
            .contentType("application/json")
            .body("\"hello world\"")
            .when()
            .post("/typed-publisher/string-cloudevents")
            .then()
            .statusCode(200)
            .body("status", equalTo("published"))
            .body("ceSource", notNullValue())
            .body("ceId", notNullValue())
            .body("ceTime", notNullValue());
    }

    /**
     * Test that CloudEvents publishing for TestOrder works.
     * Verifies metadata generation.
     */
    @Test
    public void testPublishOrderCloudEvents() {
        TestOrder order = new TestOrder("ORD-456", 150);

        given()
            .contentType("application/json")
            .body(order)
            .when()
            .post("/typed-publisher/order-cloudevents")
            .then()
            .statusCode(200)
            .body("status", equalTo("published"))
            .body("ceSource", notNullValue())
            .body("ceId", notNullValue())
            .body("ceTime", notNullValue());
    }
}
