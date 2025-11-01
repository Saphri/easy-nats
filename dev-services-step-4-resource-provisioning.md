# NATS Dev Services - Step 4: Resource Provisioning

**Goal:** Connect to the Dev Services NATS container and create the streams and consumers that were parsed in the previous step.

**Context:** This step connects the configuration loading (Step 3) with the running container (Step 2). It's the core of the provisioning logic.

**Prerequisites:**
*   `dev-services-step-2-container-integration.md` is complete.
*   `dev-services-step-3-config-loading.md` is complete.

**Tasks:**

1.  **Establish a NATS Connection:**
    *   In your `DevServicesProvisioner` (or a similar helper class), create a method that establishes a temporary connection to the NATS server using the container's URL.
    *   This connection will be used exclusively for the provisioning process. Ensure it is closed in a `finally` block.

2.  **Implement the Provisioning Logic:**
    *   Create a public method in your `DevServicesProvisioner`, e.g., `provision(Connection natsConnection, List<StreamConfiguration> streams, List<ConsumerConfiguration> consumers)`.
    *   This method must perform the creation in the correct order.
    *   **Create Streams:**
        *   Get a `JetStreamManagement` instance from the NATS connection.
        *   Iterate through the list of `StreamConfiguration` objects.
        *   For each configuration, create the stream using `jsm.addStream(streamConfig)`. It's good practice to check if the stream exists first or to use `updateStream` to make the process idempotent.
        *   Log the outcome of each creation (e.g., "Created stream 'ORDERS'").
    *   **Create Consumers:**
        *   *After* all streams have been created, iterate through the list of `ConsumerConfiguration` objects.
        *   For each configuration, create the consumer on its respective stream using `jsm.addOrUpdateConsumer(streamName, consumerConfig)`.
        *   Log the outcome of each creation (e.g., "Created consumer 'orders-processor' on stream 'ORDERS'").

3.  **Integrate with the Processor:**
    *   In `NATS_DevServicesProcessor`, after the container has started and the configurations have been parsed:
        *   Establish the connection to the NATS container.
        *   Call the `provisioner.provision(...)` method with the connection and the sorted lists of configurations.
        *   Close the connection.

**Verification:**

*   Run the application in dev mode with the sample JSON configuration files from the previous step.
*   Check the application logs to confirm that the processor is logging the successful creation of both the "ORDERS" stream and the "orders-processor" consumer.
*   (Optional) Use a NATS tool (like `nats-cli`) to connect to the running Testcontainer and verify that the stream and consumer exist.

This step is complete when the Dev Services processor automatically creates all streams and consumers from the configuration files on the managed NATS server at startup.
