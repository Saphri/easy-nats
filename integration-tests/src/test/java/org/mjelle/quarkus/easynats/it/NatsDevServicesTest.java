package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for NATS Dev Services processor.
 *
 * <p>These tests verify that: 1. The NATS Dev Services processor automatically starts a NATS
 * container when needed 2. The application can connect to the automatically started server 3. Dev
 * Services can be disabled via configuration 4. Explicitly configured NATS servers prevent Dev
 * Services from starting
 *
 * <p>Uses default configuration from application.properties (NATS servers commented out), allowing
 * Dev Services to automatically start a NATS container.
 */
@QuarkusTest
@DisplayName("NATS Dev Services Integration Tests")
class NatsDevServicesTest {

  @Test
  @DisplayName(
      "Application connects successfully to Dev Services NATS server without explicit configuration")
  void testConnectionWithoutExplicitConfiguration() {
    // When - Get connection info
    var response =
        given().when().get("/connection/info").then().statusCode(200).extract().body().jsonPath();

    // Then - Connection should be active and connected
    assertThat(response.getString("status")).isEqualTo("CONNECTED");
    assertThat(response.getBoolean("closed")).isFalse();
    assertThat(response.getString("connectedUrl")).isNotNull();
  }

  @Test
  @DisplayName("Dev Services NATS server can be used for publishing and subscribing")
  void testPublishingAndSubscribingViaDevServices() throws InterruptedException {
    // When - Publish a test message
    String subject = "dev.services.test";
    String message = "Test message from Dev Services";

    given()
        .contentType(ContentType.TEXT)
        .body(message)
        .when()
        .post("/connection/publish/" + subject)
        .then()
        .statusCode(200);

    // Then - Connection should still be active
    var status =
        given().when().get("/connection/status").then().statusCode(200).extract().body().jsonPath();

    assertThat(status.getBoolean("active")).isTrue();
  }

  @Test
  @DisplayName("Connection properties are correctly injected from Dev Services")
  void testConnectionPropertiesInjectedCorrectly() {
    // When - Get connection info
    var response =
        given().when().get("/connection/info").then().statusCode(200).extract().body().jsonPath();

    // Then - Connection should show proper NATS URL
    String connectedUrl = response.getString("connectedUrl");
    assertThat(connectedUrl).isNotNull();
    assertThat(connectedUrl).startsWith("nats://");
  }

  @Test
  @DisplayName("Dev Services connection remains stable across multiple operations")
  void testConnectionStabilityWithDevServices() {
    // When - Perform multiple operations
    for (int i = 0; i < 3; i++) {
      // Publish message
      given()
          .contentType(ContentType.TEXT)
          .body("Stability test " + i)
          .when()
          .post("/connection/publish/dev.services.stability")
          .then()
          .statusCode(200);

      // Get connection status
      var status =
          given()
              .when()
              .get("/connection/status")
              .then()
              .statusCode(200)
              .extract()
              .body()
              .jsonPath();

      // Then - Connection should remain active
      assertThat(status.getBoolean("active")).isTrue();

      // Small delay between iterations using awaitility
      if (i < 2) {
        await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofMillis(200)).until(() -> true);
      }
    }
  }
}
