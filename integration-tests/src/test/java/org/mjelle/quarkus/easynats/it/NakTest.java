package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.model.OrderData;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for negative acknowledgment (NatsMessage.nak()) functionality.
 *
 * <p>These tests verify that: 1. Subscribers can receive NatsMessage&lt;T&gt; parameter and call
 * nak() 2. Metadata provides redelivery count information 3. Framework skips auto-ack/nak when
 * explicit mode is used 4. Payload is properly deserialized and accessible 5. Subscribers can
 * distinguish between first attempt and redelivery attempts
 */
@QuarkusTest
@DisplayName("Negative Ack (NatsMessage.nak()) Integration Tests")
class NakTest {

  @BeforeEach
  void setUp() throws Exception {
    // Reset subscriber state before each test
    given().when().post("/nak/reset").then().statusCode(204);

    // Purge stream to start clean
    NatsTestUtils.purgeStream();
  }

  @AfterEach
  void tearDown() throws Exception {
    NatsTestUtils.purgeStream();
  }

  @Test
  @DisplayName("US2-T1: Subscriber receives redelivery count via msg.metadata()")
  void testSubscriberReceivesRedeliveryCount() {
    // Given: An order to publish
    OrderData order = new OrderData("ORD-NAK-001", "CUST-NAK-001", 150.00);

    // When: We publish the order and wait for processing
    given()
        .contentType(ContentType.JSON)
        .body(order)
        .when()
        .post("/nak/publish")
        .then()
        .statusCode(204);

    // Then: Verify subscriber received message with metadata
    // On first delivery, subscriber calls nak() to request redelivery
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              // First redelivery should be counted
              Integer redeliveryCount =
                  given()
                      .when()
                      .get("/nak/redelivery-count")
                      .then()
                      .statusCode(200)
                      .extract()
                      .jsonPath()
                      .getInt("redeliveryCount");

              assertThat(redeliveryCount).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  @DisplayName("US2-T2: Subscriber can call nak() without error and request redelivery")
  void testSubscriberCanCallNakWithoutError() {
    // Given: An order to publish
    OrderData order = new OrderData("ORD-NAK-002", "CUST-NAK-002", 250.00);

    // When: We publish the order
    given()
        .contentType(ContentType.JSON)
        .body(order)
        .when()
        .post("/nak/publish")
        .then()
        .statusCode(204);

    // Then: Verify the subscriber processed the message (called nak() on first attempt, then ack()
    // on second)
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              OrderData result =
                  given()
                      .when()
                      .get("/nak/last-processed")
                      .then()
                      .statusCode(200)
                      .extract()
                      .as(OrderData.class);

              assertThat(result).isEqualTo(order);
            });

    // Verify no errors occurred during processing
    given().when().get("/nak/error").then().statusCode(404); // 404 means no error was stored
  }

  @Test
  @DisplayName("US2-T3: Payload is deserialized correctly before being wrapped in NatsMessage")
  void testPayloadIsDeserializedCorrectlyWithNak() {
    // Given: Multiple orders with different data
    OrderData[] orders = {
      new OrderData("ORD-NAK-003", "CUST-NAK-003", 350.00),
      new OrderData("ORD-NAK-004", "CUST-NAK-004", 450.00),
    };

    // When: We publish the first order
    given()
        .contentType(ContentType.JSON)
        .body(orders[0])
        .when()
        .post("/nak/publish")
        .then()
        .statusCode(204);

    // Then: Verify correct deserialization on redelivery
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              OrderData result =
                  given()
                      .when()
                      .get("/nak/last-processed")
                      .then()
                      .statusCode(200)
                      .extract()
                      .as(OrderData.class);

              assertThat(result).isEqualTo(orders[0]);
              assertThat(result.orderId()).isEqualTo("ORD-NAK-003");
              assertThat(result.customerId()).isEqualTo("CUST-NAK-003");
              assertThat(result.totalPrice()).isEqualTo(350.00);
            });
  }

  @Test
  @DisplayName("US2-T4: Subscriber can access headers and subject via NatsMessage")
  void testSubscriberCanAccessHeadersAndSubject() {
    // Given: An order to publish
    OrderData order = new OrderData("ORD-NAK-005", "CUST-NAK-005", 550.00);

    // When: We publish the order
    given()
        .contentType(ContentType.JSON)
        .body(order)
        .when()
        .post("/nak/publish")
        .then()
        .statusCode(204);

    // Then: Verify subscriber received message and accessor methods didn't throw
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              OrderData result =
                  given()
                      .when()
                      .get("/nak/last-processed")
                      .then()
                      .statusCode(200)
                      .extract()
                      .as(OrderData.class);

              assertThat(result).isEqualTo(order);
            });

    // Verify no errors (accessor methods would have thrown if they failed)
    given().when().get("/nak/error").then().statusCode(404);
  }
}
