package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.model.OrderData;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for CloudEvent binary-mode unwrapping and edge cases.
 *
 * <p>Tests the CloudEventUnwrapper functionality with various CloudEvent scenarios: 1. Valid
 * binary-mode CloudEvents with proper header structure 2. CloudEvents with different data types 3.
 * CloudEvents with optional vs required attributes
 */
@QuarkusTest
@DisplayName("CloudEvent Binary-Mode Integration Tests")
class CloudEventTest {

  @Test
  @DisplayName("USR-001: Valid CloudEvent binary-mode with POJO data is unwrapped and deserialized")
  void testValidCloudEventBinaryMode() {
    // Given
    OrderData orderData = new OrderData("ORD-BINARY-001", "CUST-123", 150.00);

    // When - Publish CloudEvent in binary-mode with all required headers
    given()
        .contentType(ContentType.JSON)
        .body(orderData)
        .when()
        .post("/publish/order")
        .then()
        .statusCode(204);

    // Then - Verify order was received and deserialized
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              OrderData result =
                  given()
                      .when()
                      .get("/subscribe/last-order")
                      .then()
                      .statusCode(200)
                      .extract()
                      .as(OrderData.class);

              assertThat(result).isEqualTo(orderData);
            });
  }
}
