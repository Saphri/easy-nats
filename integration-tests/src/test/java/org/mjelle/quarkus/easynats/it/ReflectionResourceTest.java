package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ReflectionResourceTest {

    @BeforeEach
    void setUp() {
        given()
                .when().get("/reflection/clear")
                .then()
                .statusCode(200);
    }

    @Test
    void testReflectionWithRestAssured() {
        given()
                .queryParam("message", "hello from rest assured")
                .when().get("/reflection/publish")
                .then()
                .statusCode(200)
                .body(is("Message published"));

        given()
                .when().get("/reflection/messages")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].message", is("hello from rest assured"));
    }
}
