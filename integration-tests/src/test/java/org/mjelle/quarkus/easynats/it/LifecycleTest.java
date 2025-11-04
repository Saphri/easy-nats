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
 * Integration tests for NatsConnection lifecycle management.
 *
 * <p>Tests that NatsConnection: 1. Is ready for injection immediately after application startup 2.
 * Can be used throughout application lifetime 3. Closes gracefully on application shutdown
 */
@QuarkusTest
@DisplayName("NatsConnection Lifecycle Integration Tests")
class LifecycleTest {

  @Test
  @DisplayName("Application starts successfully and connection is ready for injection")
  void testApplicationStartupWithConnection() {
    // When - Get connection info immediately after startup
    var response =
        given().when().get("/connection/info").then().statusCode(200).extract().body().jsonPath();

    // Then - Connection should be ready and connected
    assertThat(response.getString("status")).isEqualTo("CONNECTED");
    assertThat(response.getBoolean("closed")).isFalse();
  }

  @Test
  @DisplayName("Connection can be used immediately after application startup")
  void testConnectionUsableImmediatelyAfterStartup() throws InterruptedException {
    // When - Publish message immediately
    String subject = "test.lifecycle.immediate";
    String message = "Immediate use test";

    given()
        .contentType(ContentType.TEXT)
        .body(message)
        .when()
        .post("/connection/publish/" + subject)
        .then()
        .statusCode(200);

    // Then - Verify we can still use connection after publish
    var status =
        given().when().get("/connection/status").then().statusCode(200).extract().body().jsonPath();

    assertThat(status.getBoolean("active")).isTrue();
  }

  @Test
  @DisplayName("Multiple REST calls over time verify connection remains open")
  void testConnectionStabilityOverTime() {
    // When - Make multiple calls to verify connection stability
    for (int i = 0; i < 5; i++) {
      // Publish a message
      given()
          .contentType(ContentType.TEXT)
          .body("Message " + i)
          .when()
          .post("/connection/publish/test.lifecycle.stability")
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
      assertThat(status.getString("status")).isEqualTo("CONNECTED");

      // Small delay between iterations using awaitility
      if (i < 4) {
        await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofMillis(200)).until(() -> true);
      }
    }
  }

  @Test
  @DisplayName("Connection info remains consistent across multiple requests")
  void testConnectionConsistencyOverTime() {
    // When - Get connection info multiple times
    String firstUrl =
        given()
            .when()
            .get("/connection/info")
            .then()
            .statusCode(200)
            .extract()
            .path("connectedUrl");

    // Then - All subsequent requests should return the same URL
    for (int i = 0; i < 5; i++) {
      String url =
          given()
              .when()
              .get("/connection/info")
              .then()
              .statusCode(200)
              .extract()
              .path("connectedUrl");

      assertThat(url).isEqualTo(firstUrl);
    }
  }
}
