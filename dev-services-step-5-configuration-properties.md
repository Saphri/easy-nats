# NATS Dev Services - Step 5: Configuration Properties

**Goal:** Expose configuration properties to the user so they can enable/disable the Dev Service and its features.

**Context:** This step makes the new Dev Services feature flexible and configurable, adhering to Quarkus conventions.

**Prerequisites:**
*   `dev-services-step-2-container-integration.md` is complete.
*   `dev-services-step-3-config-loading.md` is complete.
*   `dev-services-step-4-resource-provisioning.md` is complete.

**Tasks:**

1.  **Define Configuration Group:**
    *   In the `runtime` module, create a new configuration group class for Dev Services.
    *   **File:** `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/config/DevServicesConfig.java`
    *   **Annotation:** `@ConfigGroup`

2.  **Add Properties to the Configuration Group:**
    *   Inside `DevServicesConfig`, define the properties that users can configure. Each property should have a Javadoc description and a default value.
    *   `enabled`: A boolean to enable or disable the Dev Service entirely. Default: `true`.
    *   `imageName`: The Docker image to use for the NATS container. Default: `"nats:2.11"`.
    *   `provisioning.enabled`: A boolean to control the automatic provisioning of resources from configuration files. Default: `true`.

3.  **Create a Root Configuration Class for Dev Services:**
    *   Create a new root configuration class to hold the `DevServicesConfig`.
    *   **File:** `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/config/NatsDevServicesConfiguration.java`
    *   **Annotation:** `@ConfigRoot(name = "easynats.devservices", phase = ConfigPhase.BUILD_TIME)`
    *   **Content:** It should contain a single field for the `DevServicesConfig` group.

4.  **Integrate Configuration into the Processor:**
    *   In `NatsDevServicesProcessor`, you can now inject your `NatsDevServicesConfiguration` class directly into the build step method.
    *   Use the injected configuration to control the logic:
        *   Check `config.devservices.enabled` before starting the container.
        *   Use `config.devservices.imageName` when creating the `NatsContainer`.
        *   Check `config.devservices.provisioning.enabled` before running the provisioning logic.

5.  **Document the Properties:**
    *   In `docs/CONFIGURATION.md`, add a new section for "Dev Services Configuration".
    *   List the new properties, their types, default values, and a brief description of what they do.

**Verification:**

*   Add `quarkus.easynats.devservices.enabled=false` to `integration-tests/src/test/resources/application.properties`.
*   Run in dev mode and verify that the NATS container does **not** start.
*   Remove the property, run again, and verify the container starts.
*   Add `quarkus.easynats.devservices.provisioning.enabled=false`.
*   Run in dev mode and verify that the container starts, but the logs **do not** show any stream or consumer creation messages.

This step is complete when the Dev Services behavior can be fully controlled by the user via `application.properties`.
