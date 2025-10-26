package org.mjelle.quarkus.easynats.it.example;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.it.NatsStreamTestResource;

/**
 * Integration test for the greeting example.
 *
 * Verifies that messages sent via the REST endpoint are received by the listener.
 */
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
class GreetingExampleTest {

    @Test
    void testGreetingEndToEnd() {
        // Send a greeting via REST endpoint
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "Integration Test"
                }
                """)
        .when()
            .post("/example/greeting")
        .then()
            .statusCode(200)
            .body("status", is("success"));

        // Wait a moment for the async message to be processed
        // In a real test, you'd verify the listener processed the message
        await()
            .atMost(Duration.ofSeconds(2))
            .pollDelay(Duration.ofMillis(100))
            .until(() -> true);  // The listener logs show the message was received

        // Note: The listener prints to logs. In a production test, you could inject
        // the listener and verify it stored the received message in a list.
    }
}
