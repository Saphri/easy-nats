# Guide: Creating Native Integration Tests for Quarkus EasyNATS

This document provides a comprehensive guide and template for creating new native integration tests for this project. To ensure consistency and correctness, follow these instructions precisely.

## Core Principles

1.  **Native First:** All integration tests **must** be written to pass in native mode (`-Pnative`).
2.  **REST Assured:** Tests interact with the application via REST endpoints. The application logic under test will use NATS, but the test itself will trigger and verify behavior through HTTP calls.
3.  **Managed NATS Server:** You **do not** need to start a NATS server manually. The test harness does this automatically using a `@QuarkusTestResource`, which is configured to use the `docker-compose-devservices.yml` file.

---

## Test Class Template

All new integration tests must follow this structure. Use this template as a starting point.

```java
package org.mjelle.quarkus.easynats.it; // Or a suitable sub-package

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

/**
 * A clear description of what this test class verifies.
 */
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class) // This line is ESSENTIAL to start the NATS server
class MyNewFeatureIT {

    @Test
    @DisplayName("A clear, descriptive name for the test case")
    void testMyNewFeature() {
        // Arrange: Create the payload object you intend to send.
        var eventPayload = new MyTestEvent("some-data");

        // Act: Call a REST endpoint in the test application that publishes a NATS message.
        given()
            .contentType(ContentType.JSON)
            .body(eventPayload)
            .when()
            .post("/my-test-endpoint/publish")
            .then()
            .statusCode(204); // Or 200, etc.

        // Assert: Verify that the NATS subscriber in the application received the message
        // and processed it correctly. This is usually done by calling another REST endpoint
        // that exposes the result of the subscriber's work.
        // Use Awaitility to handle the asynchronous nature of messaging.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String result = given()
                .when()
                .get("/my-test-endpoint/get-last-message")
                .then()
                .statusCode(200)
                .extract().asString();

            assertThat(result).isEqualTo("some-data");
        });
    }
}
```

---

## Supporting Application Code

Remember that for a test to work, the test application itself (located in `integration-tests/src/main/java`) needs to have the necessary components:

1.  **A REST Endpoint for Publishing:** This is what your test calls to trigger the NATS message.
    ```java
    @Path("/my-test-endpoint")
    public class MyTestResource {
        @Inject
        NatsPublisher<MyTestEvent> publisher;

        @POST
        @Path("/publish")
        public void publish(MyTestEvent event) {
            publisher.publish("subject-for-testing", event);
        }
    }
    ```

2.  **A NATS Subscriber:** This is the component you are actually testing.
    ```java
    @ApplicationScoped
    public class MyTestSubscriber {
        // Store the last received message in memory so the test can verify it.
        private volatile String lastMessageData;

        @NatsSubscriber(subject = "subject-for-testing")
        public void onMessage(MyTestEvent event) {
            this.lastMessageData = event.getData();
        }

        public String getLastMessageData() {
            return lastMessageData;
        }
    }
    ```

3.  **A REST Endpoint for Verification:** This is what your test calls to check the result.
    ```java
    // Add this to MyTestResource.java
    @Inject
    MyTestSubscriber subscriber;

    @GET
    @Path("/get-last-message")
    public String getLastMessage() {
        return subscriber.getLastMessageData();
    }
    ```

## How to Run the Tests

To run all integration tests, including the native ones, use the following command from the project root:

```bash
./mvnw clean install -Pnative -pl integration-tests
```
