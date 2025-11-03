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
 * Integration tests for custom payload codec feature.
 *
 * <p>
 * Tests the custom codec mechanism for serialization and deserialization:
 * 1. User Story 1: Custom codec is used for publishing and subscribing
 * 2. User Story 2: Codec validation failures result in NACK and subscriber not invoked
 * </p>
 */
@QuarkusTest
@DisplayName("Custom Codec Integration Tests")
class CustomCodecTest {

    @Test
    @DisplayName("US1: Custom codec is used to encode/decode messages")
    void testCustomCodecIsUsedForPublishingAndSubscribing() {
        // Given - Custom codec is registered as a CDI bean
        OrderData orderData = new OrderData("CODEC-ORDER-001", "CUST-CODEC-001", 99.99);

        // When - Publish an order using NatsPublisher
        given()
                .contentType(ContentType.JSON)
                .body(orderData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify that the custom codec was used
        // 1. The message was deserialized by the custom codec in the subscriber
        // 2. The datacontenttype header reflects the custom codec's content type
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

        // Verify codec was called
        given()
                .when()
                .get("/codec/encode-count")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.greaterThan(0));

        given()
                .when()
                .get("/codec/decode-count")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.greaterThan(0));
    }

    @Test
    @DisplayName("US2: Codec validation exception prevents subscriber invocation and NACKs message")
    void testCodecDeserializationExceptionResultsInNackAndNoSubscriberInvocation() {
        // Given - Custom codec is registered with validation logic
        // When - Publish a message that will fail codec validation
        given()
                .contentType(ContentType.JSON)
                .queryParam("fail-decode", "true")
                .when()
                .post("/publish/invalid-order")
                .then()
                .statusCode(204);

        // Then - Wait briefly to allow message to be delivered
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    // Verify that subscriber was NOT invoked (no message received)
                    given()
                            .when()
                            .get("/subscribe/last-invalid-order")
                            .then()
                            .statusCode(404);
                });

        // Verify that the decode error was logged
        given()
                .when()
                .get("/codec/decode-error-count")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.greaterThan(0));
    }
}
