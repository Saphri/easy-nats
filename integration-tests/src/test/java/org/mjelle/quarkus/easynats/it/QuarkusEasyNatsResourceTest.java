package org.mjelle.quarkus.easynats.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusEasyNatsResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/quarkus-easy-nats")
                .then()
                .statusCode(200)
                .body(is("Hello quarkus-easy-nats"));
    }
}
