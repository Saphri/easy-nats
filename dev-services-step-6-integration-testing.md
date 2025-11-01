# NATS Dev Services - Step 6: Integration Testing

**Goal:** Create a comprehensive native integration test that verifies the entire Dev Services feature from end to end.

**Context:** This is the final and most critical step. A successful test here proves that all the previous steps have been integrated correctly and the feature works as intended for the end-user.

**Prerequisites:** All previous steps (1-5) are complete.

**Tasks:**

1.  **Create Test Configuration Files:**
    *   In the `integration-tests` module, under `src/test/resources/`, create the directory `nats-dev-services`.
    *   Inside this directory, add a `stream.json` file defining a test stream (e.g., "TEST.STREAM").
    *   Add a `consumer.json` file defining a durable consumer on that stream (e.g., "test-consumer").

2.  **Create a New Test Class:**
    *   In the `integration-tests` module, create a new test class, e.g., `DevServicesIT.java`.
    *   **Crucially, this test class should NOT have the `@QuarkusTestResource` annotation.** The Dev Service should be activated automatically because no `quarkus.easynats.servers` property is set.

3.  **Write the Test Method:**
    *   **Refer to the `integration-test-creation-guide.md` file for the correct structure and conventions.**
    *   The test should verify the complete end-to-end flow:
        1.  **Arrange:** The test application should have a durable `@NatsSubscriber` that matches the consumer defined in your `consumer.json`. It should also have a REST endpoint to publish a message and another to retrieve the result.
        2.  **Act:** The test calls the REST endpoint to publish a message.
        3.  **Assert:** The test calls the other REST endpoint (using `Awaitility`) to verify that the subscriber received the message and processed it correctly.

4.  **Ensure No NATS Configuration in `application.properties`:**
    *   Double-check that `integration-tests/src/test/resources/application.properties` does **not** contain a `quarkus.easynats.servers` property for this test. The absence of this property is what triggers the Dev Service.

5.  **Run the Test in Native Mode:**
    *   Execute the test using the native profile to confirm it works in a compiled native executable.
    *   Command: `./mvnw clean install -Pnative -pl integration-tests -Dquarkus.test.include-tags=devservices` (using a tag can help isolate the test).

**Verification:**

*   The test should pass in both JVM and native modes.
*   When the test runs, the console logs should show the NATS Testcontainer starting, followed by logs indicating that the stream and consumer from your JSON files were successfully provisioned.

This step is complete when you have a passing native integration test that relies entirely on the new Dev Services feature to provide and configure the NATS broker.
