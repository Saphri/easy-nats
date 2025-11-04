package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.model.MyArrayItemEvent;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ArrayPayloadTest {

  @Test
  @DisplayName("Should publish and receive an array of events")
  void testArrayPayload() {
    // Arrange
    MyArrayItemEvent[] eventPayload = {new MyArrayItemEvent("one"), new MyArrayItemEvent("two")};

    // Act
    given()
        .contentType(ContentType.JSON)
        .body(eventPayload)
        .when()
        .post("/array-payload/publish")
        .then()
        .statusCode(204);

    // Assert
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              MyArrayItemEvent[] result =
                  given()
                      .when()
                      .get("/array-payload/get-last-message")
                      .then()
                      .statusCode(200)
                      .extract()
                      .as(MyArrayItemEvent[].class);

              assertThat(result)
                  .extracting(MyArrayItemEvent::getData)
                  .containsExactly("one", "two");
            });
  }
}
