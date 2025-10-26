package org.mjelle.quarkus.easynats.it;

import io.nats.client.Connection;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.JetStream;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;
import static org.mjelle.quarkus.easynats.it.NatsTestUtils.purgeStream;

/**
 * Base test class for NatsPublisher integration testing.
 * Tests run in Quarkus dev mode using RestAssured to call REST endpoints.
 *
 * All test methods are concrete to support both dev-mode (@QuarkusTest) and
 * integration-test (@QuarkusIntegrationTest) contexts via inheritance.
 */
@QuarkusTest
public class BasicPublisherTest {

    private static final String SUBJECT = "test.basic_publisher";

    private Connection nc;
    private JetStreamSubscription sub;

    @BeforeEach
    void setUp() throws Exception {
        nc = NatsTestUtils.getConnection();
        purgeStream();
        JetStream js = nc.jetStream();
        sub = js.subscribe(SUBJECT);
    }

    /**
     * Test that the publisher resource health endpoint is available.
     * This verifies that NatsPublisher can be injected into the REST endpoint.
     */
    @Test
    public void testPublisherCanBeInjected() {
        given()
            .when()
            .get("/publish/health")
            .then()
            .statusCode(200)
            .body(containsString("ok"));
    }

    /**
     * Test that publisher.publish() does not throw an exception.
     * Calls the REST endpoint with a test message.
     */
    @Test
    public void testPublisherPublishesMessage() {
        given()
            .queryParam("subject", SUBJECT)
            .queryParam("message", "test message")
            .when()
            .get("/publish/message")
            .then()
            .statusCode(204);
    }

    /**
     * Test that published message appears on NATS broker at subject "test".
     *
     * This test:
     * 1. Subscribes to the "test" subject using JetStream
     * 2. Publishes a message via the REST endpoint
     * 3. Waits for the message to appear on the broker
     * 4. Verifies the received message matches the published content
     */
    @Test
    public void testMessageAppearsOnBroker() {
        // Publish test message via REST endpoint
        String testMessage = "hello world";
        given()
            .queryParam("subject", SUBJECT)
            .queryParam("message", testMessage)
            .when()
            .get("/publish/message")
            .then()
            .statusCode(204);

        // Use Awaitility to wait for message with polling (NEVER use Thread.sleep)
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                Message msg = sub.nextMessage(Duration.ofMillis(500));
                assertThat(msg).isNotNull();

                String receivedMessage = new String(msg.getData(), StandardCharsets.UTF_8);
                assertThat(receivedMessage).isEqualTo(testMessage);

                // Acknowledge the message
                msg.ack();
            });
    }
}
