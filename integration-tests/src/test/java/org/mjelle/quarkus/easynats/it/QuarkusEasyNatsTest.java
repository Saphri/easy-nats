package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for the Quarkus Easy NATS extension.
 *
 * <p>These tests verify that the extension is properly loaded and functional with a real NATS
 * instance (provided by Dev Services).
 */
@QuarkusTest
@DisplayName("Quarkus Easy NATS Extension Integration Tests")
public class QuarkusEasyNatsTest {

  @Test
  @DisplayName("Extension loads successfully and connects to NATS")
  public void testExtensionLoadsAndConnects() {
    // When - Get connection status
    var response =
        given().when().get("/connection/info").then().statusCode(200).extract().body().jsonPath();

    // Then - Connection should be established
    assertThat(response.getString("status")).isEqualTo("CONNECTED");
    assertThat(response.getBoolean("closed")).isFalse();
  }

  @Test
  @DisplayName("Extension can publish messages to NATS")
  public void testExtensionCanPublish() {
    // When - Publish a test message
    given()
        .contentType(ContentType.TEXT)
        .body("Test message")
        .when()
        .post("/connection/publish/test.extension")
        .then()
        .statusCode(200);

    // Then - Connection should remain active
    var status =
        given().when().get("/connection/status").then().statusCode(200).extract().body().jsonPath();

    assertThat(status.getBoolean("active")).isTrue();
  }

  @Test
  @DisplayName("Extension configuration is correctly injected")
  public void testConfigurationInjection() {
    // When - Get connection info
    var response =
        given().when().get("/connection/info").then().statusCode(200).extract().body().jsonPath();

    // Then - Connection details should be available
    String connectedUrl = response.getString("connectedUrl");
    assertThat(connectedUrl).isNotNull();
    assertThat(connectedUrl).startsWith("nats://");
  }
}
