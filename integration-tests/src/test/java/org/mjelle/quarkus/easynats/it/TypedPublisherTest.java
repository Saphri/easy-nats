package org.mjelle.quarkus.easynats.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.api.ConsumerConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mjelle.quarkus.easynats.it.NatsTestUtils.STREAM_NAME;
import static org.mjelle.quarkus.easynats.it.NatsTestUtils.purgeStream;

/**
 * Integration tests for TypedPublisherResource.
 * Tests typed publishing and CloudEvents publishing via REST endpoints.
 */
@QuarkusTest
public class TypedPublisherTest {

    private static final String STRING_SUBJECT = "test.typed_publisher.string";
    private static final String ORDER_SUBJECT = "test.typed_publisher.order";

    private Connection nc;

    @BeforeEach
    void setUp() throws Exception {
        nc = NatsTestUtils.getConnection();
        purgeStream();
    }


    /**
     * Test that String publishing works.
     * Returns 204 No Content on success.
     */
    @Test
    public void testPublishString() {
        given()
            .contentType("application/json")
            .body("\"hello world\"")
            .when()
            .post("/typed-publisher/string")
            .then()
            .statusCode(204);
    }

    /**
     * Test that TestOrder publishing works.
     * Returns 204 No Content on success.
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
            .statusCode(204);
    }


    /**
     * Test that published String appears on NATS broker.
     * Uses JetStream subscription to verify message delivery.
     */
    @Test
    public void testPublishedStringAppearsOnBroker() throws Exception {
        final var jsm = nc.jetStreamManagement();
        final var js = nc.jetStream();
        jsm.addOrUpdateConsumer(NatsTestUtils.STREAM_NAME, ConsumerConfiguration.builder()
            .filterSubjects(STRING_SUBJECT)
            .name("STRING_CONSUMER")
            .build());

        var ctx = js.getConsumerContext(STREAM_NAME, "STRING_CONSUMER");
        
        // Publish via REST endpoint
        String testMessage = "integration-test-message";
        given()
            .contentType("application/json")
            .body(testMessage)
            .when()
            .post("/typed-publisher/string")
            .then()
            .statusCode(204);

        // Wait for message on broker
        Message msg = ctx.next(Duration.ofSeconds(5L));
        assertThat(msg).isNotNull();
        msg.ack();

        String receivedMessage = new String(msg.getData(), StandardCharsets.UTF_8);
        assertThat(receivedMessage).isEqualTo(testMessage);

        jsm.deleteConsumer(NatsTestUtils.STREAM_NAME, "STRING_CONSUMER");
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
            .body("ceSource", notNullValue())
            .body("ceId", notNullValue())
            .body("ceTime", notNullValue())
            .body("ceType", notNullValue());
    }

    /**
     * Test that CloudEvents publishing for TestOrder works.
     * Returns 200 OK with CloudEvents metadata in response body.
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
            .body("ceSource", notNullValue())
            .body("ceId", notNullValue())
            .body("ceTime", notNullValue())
            .body("ceType", notNullValue());
    }

    /**
     * Test that published TestOrder appears on NATS broker.
     * Uses JetStream subscription to verify message delivery.
     */
    @Test
    public void testPublishedOrderAppearsOnBroker() throws Exception {
        final var jsm = nc.jetStreamManagement();
        final var js = nc.jetStream();
        jsm.addOrUpdateConsumer(NatsTestUtils.STREAM_NAME, ConsumerConfiguration.builder()
            .filterSubjects(ORDER_SUBJECT)
            .name("ORDER_CONSUMER")
            .build());

        var ctx = js.getConsumerContext(STREAM_NAME, "ORDER_CONSUMER");

        // Publish via REST endpoint
        TestOrder testOrder = new TestOrder("ORD-789", 250);
        given()
            .contentType("application/json")
            .body(testOrder)
            .when()
            .post("/typed-publisher/order")
            .then()
            .statusCode(204);

        // Wait for message on broker
        Message msg = ctx.next(Duration.ofSeconds(5L));
        assertThat(msg).isNotNull();
        msg.ack();

        // Deserialize the message payload
        ObjectMapper objectMapper = new ObjectMapper();
        TestOrder receivedOrder = objectMapper.readValue(msg.getData(), TestOrder.class);
        assertThat(receivedOrder.orderId()).isEqualTo(testOrder.orderId());
        assertThat(receivedOrder.amount()).isEqualTo(testOrder.amount());

        jsm.deleteConsumer(NatsTestUtils.STREAM_NAME, "ORDER_CONSUMER");
    }
}
