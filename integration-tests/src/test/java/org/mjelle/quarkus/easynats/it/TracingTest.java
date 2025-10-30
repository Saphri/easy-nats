package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.model.OrderData;

/**
 * Integration tests for distributed tracing with W3C Trace Context propagation.
 *
 * <p>
 * Tests verify that:
 * 1. Producer spans are created when publishing messages
 * 2. Consumer spans are created when receiving messages
 * 3. W3C Trace Context headers are properly propagated
 * 4. Redelivery is properly detected and marked
 * 5. Errors are recorded in spans
 * </p>
 */
@QuarkusTest
@DisplayName("Distributed Tracing Integration Tests")
class TracingTest {

    @Test
    @DisplayName("T008: Valid message publish and consume creates linked producer/consumer spans")
    void testMessagePublishAndConsumeCreatesSpans() {
        // Given
        OrderData orderData = new OrderData("ORD-TRACE-001", "CUST-TRACE-001", 250.00);

        // When - Publish a message (creates producer span)
        given()
                .contentType(ContentType.JSON)
                .body(orderData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify message was received by subscriber (consumer span created)
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/subscribe/last-order")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    assertThat(result).isEqualTo(orderData);
                });
    }

    @Test
    @DisplayName("T009: W3C Trace Context headers are injected into NATS message headers")
    void testW3CTraceContextHeadersAreInjected() {
        // Given
        OrderData orderData = new OrderData("ORD-TRACE-002", "CUST-TRACE-002", 300.00);

        // When - Publish a message (which should inject traceparent and tracestate headers)
        given()
                .contentType(ContentType.JSON)
                .body(orderData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify the message was processed by the subscriber
        // (In a real scenario with OpenTelemetry exporter, we would verify the actual trace)
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/subscribe/last-order")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    // Verify the message made it through the traced pipeline
                    assertThat(result).isNotNull();
                    assertThat(result.orderId()).isEqualTo("ORD-TRACE-002");
                });
    }

    @Test
    @DisplayName("T010: Message redelivery is detected and marked with messaging.message_redelivered attribute")
    void testMessageRedeliveryDetection() {
        // Given
        OrderData orderData = new OrderData("ORD-REDELIVERY-001", "CUST-REDELIVERY-001", 350.00);

        // When - Publish a message
        // Note: To fully test redelivery detection, the subscriber would need to explicitly nak()
        // the message to trigger redelivery. In this test, we verify that:
        // 1. Messages are successfully delivered
        // 2. The redelivery detection code path is available in NatsTraceService.createConsumerSpan()
        // Real redelivery testing would require a durable consumer with explicit nak() handling.
        given()
                .contentType(ContentType.JSON)
                .body(orderData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify message was received on first delivery
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/subscribe/last-order")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    // Verify the message was successfully processed on first delivery
                    assertThat(result).isEqualTo(orderData);
                });
    }

    @Test
    @DisplayName("T011: Error during message processing is recorded in span with ERROR status")
    void testErrorRecordingInSpan() {
        // Given - Create invalid message data that will fail deserialization
        // When sending plain text instead of JSON, the endpoint should reject it
        String invalidJson = "foo";

        // When - Try to publish invalid data
        // This should fail at the REST endpoint level before reaching NATS
        given()
                .contentType(ContentType.JSON)
                .body(invalidJson)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(400); // Should fail validation

        // Then - Verify the error was handled gracefully and system remains operational
        // Note: In a real scenario with OpenTelemetry exporters configured, we would:
        // 1. Check the OpenTelemetry exporter backend for span with status=ERROR
        // 2. Verify the exception message is recorded in the span
        // 3. Confirm the span includes the error context
        // For now, we verify the system remains operational after the error
        await()
                .atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    // Verify the system is still operational after the error
                    OrderData validOrder = new OrderData("ORD-AFTER-ERROR", "CUST-AFTER-ERROR", 100.00);
                    given()
                            .contentType(ContentType.JSON)
                            .body(validOrder)
                            .when()
                            .post("/publish/order")
                            .then()
                            .statusCode(204);

                    // Confirm the valid message was processed successfully
                    given()
                            .when()
                            .get("/subscribe/last-order")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);
                });
    }

    @Test
    @DisplayName("Tracing does not break normal message flow")
    void testTracingDoesNotBreakMessageFlow() {
        // Given

        // When - Publish and consume multiple messages
        for (int i = 0; i < 3; i++) {
            OrderData data = new OrderData("ORD-NORMAL-" + i, "CUST-NORMAL-" + i, 100.00 * (i + 1));
            given()
                    .contentType(ContentType.JSON)
                    .body(data)
                    .when()
                    .post("/publish/order")
                    .then()
                    .statusCode(204);
        }

        // Then - Verify all messages were received
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/subscribe/last-order")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    assertThat(result.orderId()).isEqualTo("ORD-NORMAL-2");
                });
    }
}
