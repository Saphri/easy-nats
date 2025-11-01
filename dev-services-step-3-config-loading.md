# NATS Dev Services - Step 3: Configuration Loading and Parsing

**Goal:** Implement the logic to find, read, and parse NATS resource configuration files from the classpath.

**Context:** With the container running, we now need to prepare the configurations for the resources we will create. This task focuses *only* on file discovery and deserialization. No connection to the NATS server should be made in this step.

**Tasks:**

1.  **Create a Configuration-handling Service:**
    *   In the `deployment` module, create a new helper class, for example, `DevServicesProvisioner`. This class will contain the logic for finding and parsing the configuration files.

2.  **Implement File Discovery:**
    *   The `DevServicesProvisioner` needs a method that scans the application's classpath for JSON files within a specific, conventional directory.
    *   The conventional path should be `nats-dev-services/`.
    *   This method should find all `.json` files in that directory and read their contents into a list of strings.

3.  **Implement Two-Pass Parsing Logic:**
    *   Create a method that takes the list of JSON file contents and parses them. This method **must** implement a two-pass strategy to prepare for correct creation order later.
    *   **First Pass (Scan and Sort):**
        *   Iterate through each JSON string.
        *   Use a JSON processing library (like Jackson, which is already a dependency) to parse the string into a generic JSON tree (e.g., `JsonNode`).
        *   Inspect the JSON structure to determine if it represents a Stream or a Consumer. A reliable way to differentiate is to check for the presence of a `"subjects"` field (for streams) vs. a `"stream_name"` field (for consumers).
        *   Deserialize the JSON into the appropriate `io.nats.client.api.StreamConfiguration` or `io.nats.client.api.ConsumerConfiguration` object.
        *   Add the deserialized objects to two separate lists: one for streams and one for consumers.
    *   The method should return a simple data object containing these two sorted lists.

4.  **Integrate with the Processor:**
    *   In `NatsDevServicesProcessor`, call your new `DevServicesProvisioner` to get the sorted lists of configurations.
    *   For now, you can simply log the names of the streams and consumers that were found to verify the logic is working.

**Verification:**

*   Create a `src/test/resources/nats-dev-services` directory in the `integration-tests` module.
*   Add two sample files: `orders-stream.json` and `orders-consumer.json`.
*   Run the application in dev mode.
*   Check the logs to confirm that your parsing logic correctly identified one stream ("ORDERS") and one consumer.

This step is complete when the Dev Services processor can successfully find and parse all stream and consumer configuration files into two correctly sorted lists of objects.
