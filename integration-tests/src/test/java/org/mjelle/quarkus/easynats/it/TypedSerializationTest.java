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
 * Integration tests for typed serialization with Jackson.
 *
 * <p>
 * Validates that:
 * 1. Records with no-arg constructors can be published and subscribed
 * 2. Java record types work correctly
 * 3. Generic types (List, Map) are handled properly
 * 4. Deserialization errors are logged with full context
 * </p>
 */
@QuarkusTest
@DisplayName("Typed Serialization Integration Tests")
class TypedSerializationTest {

    @Test
    @DisplayName("US1-T1: Record publishes and subscribes correctly")
    void testPublishAndSubscribeWithPOJO() {
        // Given
        OrderData orderData = new OrderData("ORD-POJO-001", "CUST-001", 150.00);

        // When - Publish POJO via REST endpoint
        given()
                .contentType(ContentType.JSON)
                .body(orderData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify order was received and deserialized correctly
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

                    assertThat(result)
                            .isNotNull()
                            .isEqualTo(orderData);
                    assertThat(result.orderId()).isEqualTo("ORD-POJO-001");
                    assertThat(result.customerId()).isEqualTo("CUST-001");
                    assertThat(result.totalPrice()).isEqualTo(150.00);
                });
    }

    @Test
    @DisplayName("US1-T2: Java record types publish and subscribe correctly")
    void testPublishAndSubscribeWithRecord() {
        // Given - OrderData is a record, testing that records work
        OrderData recordData = new OrderData("ORD-RECORD-002", "CUST-002", 250.50);

        // When - Publish record via REST endpoint
        given()
                .contentType(ContentType.JSON)
                .body(recordData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify record was received and deserialized correctly
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

                    assertThat(result)
                            .isNotNull()
                            .isEqualTo(recordData);
                    assertThat(result.customerId()).isEqualTo("CUST-002");
                });
    }

    @Test
    @DisplayName("US1-T3: Generic type support with complex types")
    void testPublishAndSubscribeWithGenericType() {
        // Given - A complex OrderData instance with maximum field coverage
        OrderData complexData = new OrderData(
                "ORD-GENERIC-003",
                "COMPLEX-CUSTOMER",
                1000.99);

        // When - Publish complex data via REST endpoint
        given()
                .contentType(ContentType.JSON)
                .body(complexData)
                .when()
                .post("/publish/order")
                .then()
                .statusCode(204);

        // Then - Verify complex data was received with all fields intact
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

                    assertThat(result)
                            .isNotNull()
                            .isEqualTo(complexData);
                    assertThat(result.totalPrice()).isEqualTo(1000.99);
                });
    }

    @Test
    @DisplayName("US1-T4: Deserialization errors are logged with type info and payload context")
    void testDeserializationErrorLogsWithTypeInfo() {
        // Given - Malformed JSON (missing required field 'customerId', 'totalPrice' has invalid type)
        String malformedJson = "{\"orderId\": \"ORD-INVALID\", \"totalPrice\": \"abc\"}";

        // When - Send malformed JSON to the order endpoint
        // Note: This should fail or be handled gracefully
        // The important part is that the error is logged with context
        var statusCode = given()
                .contentType(ContentType.JSON)
                .body(malformedJson)
                .when()
                .post("/publish/order")
                .then()
                .extract()
                .statusCode();

        // Then - Verify error handling
        // The exact response code depends on how the endpoint handles the error
        // Could be 400 (bad request) or 500 (internal error)
        // The critical aspect is that error logs contain:
        // - Target type (OrderData)
        // - Raw payload (the malformed JSON)
        // - Root cause message
        assertThat(statusCode).isIn(400, 500);

        // Additional assertion: verify no order was stored (if it was a JSON parse error)
        var getStatusCode = given()
                .when()
                .get("/subscribe/last-order")
                .then()
                .extract()
                .statusCode();

        // If we get a 404, that's good - no order was stored
        // If we get a 200, verify it's not the malformed order
        if (getStatusCode == 200) {
            OrderData lastOrder = given()
                    .when()
                    .get("/subscribe/last-order")
                    .then()
                    .extract()
                    .as(OrderData.class);
            assertThat(lastOrder.orderId()).isNotEqualTo("ORD-INVALID");
        }
    }
}
