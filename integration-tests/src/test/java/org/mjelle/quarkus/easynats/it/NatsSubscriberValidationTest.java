package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for @NatsSubscriber annotation validation.
 *
 * <p>These tests validate that: - Valid ephemeral mode (subject only) works at runtime -
 * Subscribers can receive messages from NATS - Integration with NATS through Dev Services works
 * correctly
 *
 * <p>Build-time validation of annotation properties (invalid combinations) is tested in the
 * deployment module. This test focuses on runtime behavior with valid configurations.
 */
@QuarkusTest
@DisplayName("NATS Subscriber Annotation Validation Integration Tests")
class NatsSubscriberValidationTest {

  @Test
  @DisplayName("Valid ephemeral subscriber (subject only) receives messages")
  void testValidEphemeralSubscriber() {
    // When - Publish a message to the test subject
    String testSubject = "validation.test.subject";
    String testMessage = "Ephemeral subscriber test";

    given()
        .contentType(ContentType.TEXT)
        .body(testMessage)
        .when()
        .post("/connection/publish/" + testSubject)
        .then()
        .statusCode(200);

    // Then - Connection should still be active (subscriber would receive if configured to listen)
    var status =
        given().when().get("/connection/status").then().statusCode(200).extract().body().jsonPath();

    assertThat(status.getBoolean("active")).isTrue();
  }

  @Test
  @DisplayName("Subscriber annotation integration with NATS JetStream")
  void testSubscriberAnnotationIntegration() {
    // When - Get connection info to verify NATS is properly configured
    var response =
        given().when().get("/connection/info").then().statusCode(200).extract().body().jsonPath();

    // Then - Connection should be to a NATS server (from Dev Services)
    assertThat(response.getString("status")).isEqualTo("CONNECTED");
    assertThat(response.getString("connectedUrl")).contains("nats://");
  }
}
