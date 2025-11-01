# NATS Dev Services - Step 2: Basic Container Integration

**Goal:** Implement the Dev Services processor that uses the custom `NatsContainer` to automatically start a NATS server.

**Context:** This step connects the `NatsContainer` class created previous to the Quarkus build process. The focus is solely on launching the container and injecting its configuration into the application.

**Tasks:**

1.  **Add Dev Services Dependency:**
    *   In `deployment/pom.xml`, add the `io.quarkus:quarkus-devservices-common` dependency.

2.  **Create the Dev Services Processor:**
    *   In the `deployment` module, create a new class named `NatsDevServicesProcessor`.

3.  **Implement the Container Launch Logic:**
    *   Within `NatsDevServicesProcessor`, create a `BuildStep` that produces a `DevServicesResultBuildItem`.
    *   This build step should only execute if Dev Services for NATS is enabled and the user has **not** configured `quarkus.easynats.servers`.
    *   Inside the build step:
        *   Instantiate your custom `NatsContainer` class. You can use a default image like `"nats:2.11"`.
        *   Start the container.
        *   Once the container is running, call the `getNatsUrl()` helper method to get the connection string.
        *   Return a new `DevServicesResultBuildItem` with the configuration key `"quarkus.easynats.servers"` and the value set to the container's NATS URL.

4.  **Verify Manually:**
    *   Run the application in dev mode (`./mvnw quarkus:dev`) from the `integration-tests` module.
    *   Ensure you have **no** `quarkus.easynats.servers` property set in `application.properties`.
    *   You should see the Testcontainers logs in the console, indicating that your custom `NatsContainer` has started. The application should connect to it successfully.

This step is complete when the application can automatically start a NATS container using your custom class and connect to it in dev mode.
