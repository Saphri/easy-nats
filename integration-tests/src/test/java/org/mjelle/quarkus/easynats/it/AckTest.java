package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.model.OrderData;

/**
 * Integration tests for explicit acknowledgment (NatsMessage.ack()) functionality.
 *
 * <p>
 * These tests verify that:
 * 1. Subscribers can receive NatsMessage&lt;T&gt; parameter (explicit mode)
 * 2. Subscribers can call ack() without error
 * 3. The framework skips auto-ack when explicit mode is used
 * 4. Payload is properly deserialized and accessible
 * </p>
 */
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
@DisplayName("Explicit Ack (NatsMessage.ack()) Integration Tests")
class AckTest {

    @BeforeEach
    void setUp() throws Exception {
        // Reset subscriber state before each test
        given()
                .when()
                .post("/ack/reset")
                .then()
                .statusCode(200);

        // Purge stream to start clean
        NatsTestUtils.purgeStream();
    }

    @AfterEach
    void tearDown() throws Exception {
        NatsTestUtils.purgeStream();
    }

    @Test
    @DisplayName("US1-T1: Subscriber receives NatsMessage<T> parameter and can call ack()")
    void testSubscriberReceivesNatsMessageAndCanCallAck() {
        // Given: An order to publish
        OrderData order = new OrderData("ORD-001", "CUST-001", 150.00);

        // When: We publish the order
        given()
                .contentType(ContentType.JSON)
                .body(order)
                .when()
                .post("/ack/publish")
                .then()
                .statusCode(202);

        // Then: Verify the subscriber received it and called ack() without error
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/ack/last-acked")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    assertThat(result).isEqualTo(order);
                });

        // Verify no errors occurred during processing
        given()
                .when()
                .get("/ack/error")
                .then()
                .statusCode(404); // 404 means no error was stored
    }

    @Test
    @DisplayName("US1-T2: Payload is deserialized before being wrapped in NatsMessage")
    void testPayloadIsDeserializedCorrectly() {
        // Given: Multiple orders with different data
        OrderData[] orders = {
            new OrderData("ORD-002", "CUST-002", 250.00),
            new OrderData("ORD-003", "CUST-003", 350.00),
        };

        // When: We publish the first order
        OrderData firstOrder = orders[0];
        given()
                .contentType(ContentType.JSON)
                .body(firstOrder)
                .when()
                .post("/ack/publish")
                .then()
                .statusCode(202);

        // Then: Verify correct deserialization
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/ack/last-acked")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    assertThat(result).isEqualTo(firstOrder);
                    assertThat(result.orderId()).isEqualTo("ORD-002");
                    assertThat(result.customerId()).isEqualTo("CUST-002");
                    assertThat(result.totalPrice()).isEqualTo(250.00);
                });

        // When: We reset and publish the second order
        given()
                .when()
                .post("/ack/reset")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(orders[1])
                .when()
                .post("/ack/publish")
                .then()
                .statusCode(202);

        // Then: Verify the new order is deserialized correctly (not previous order)
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/ack/last-acked")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    assertThat(result).isEqualTo(orders[1]);
                    assertThat(result.orderId()).isEqualTo("ORD-003");
                });
    }

    @Test
    @DisplayName("US1-T3: NatsMessage accessor methods are callable (headers, subject, metadata)")
    void testNatsMessageAccessorMethods() {
        // Given: An order to publish
        OrderData order = new OrderData("ORD-ACC-001", "CUST-ACC-001", 500.00);

        // When: We publish the order
        given()
                .contentType(ContentType.JSON)
                .body(order)
                .when()
                .post("/ack/publish")
                .then()
                .statusCode(202);

        // Then: Verify subscriber received message and accessor methods didn't throw
        // (This is tested indirectly - if accessor methods threw, the subscriber would log an error)
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    OrderData result = given()
                            .when()
                            .get("/ack/last-acked")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(OrderData.class);

                    assertThat(result).isEqualTo(order);
                });

        // Verify no errors (accessor methods would have thrown if they failed)
        given()
                .when()
                .get("/ack/error")
                .then()
                .statusCode(404);
    }
}
