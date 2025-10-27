# API Contract: NatsPublisher<T>

This document defines the changes to the `NatsPublisher<T>` public API.

## Current State (To Be Removed)

The existing API is generic but has separate methods for raw publishing and CloudEvent publishing.

```java
public class NatsPublisher<T> {
    // ... constructors and private methods

    public void publish(T payload) throws Exception;
    public void publish(String subject, T payload) throws Exception;

    public CloudEventsHeaders.CloudEventsMetadata publishCloudEvent(T payload, String ceType, String ceSource) throws Exception;
    public CloudEventsHeaders.CloudEventsMetadata publishCloudEvent(String subject, T payload, String ceType, String ceSource) throws Exception;
}
```

## New State (Proposed)

The API will be simplified to only include the `publish` methods, which will now handle CloudEvent creation transparently. The `publishCloudEvent` methods will be removed.

```java
public class NatsPublisher<T> {

    /**
     * Publishes a typed payload to the default NATS subject (configured via @NatsSubject),
     * automatically wrapping it in a CloudEvent envelope.
     *
     * @param payload The payload object to publish.
     */
    public void publish(T payload) throws Exception;

    /**
     * Publishes a typed payload to the specified NATS subject, automatically wrapping it
     * in a CloudEvent envelope.
     *
     * @param subject The NATS subject to publish the message to.
     * @param payload The payload object to publish.
     */
    public void publish(String subject, T payload) throws Exception;
}
```

### Behavior Changes

-   **`publish(T payload)` and `publish(String subject, T payload)`**:
    -   **Old Behavior**: Sent the payload with basic encoding, without any CloudEvents headers.
    -   **New Behavior**: These methods will now always wrap the payload in a CloudEvent. They will generate all required `ce-` headers and serialize the body appropriately, making CloudEvents the default transport.
-   **`publishCloudEvent(...)`**:
    -   **Old Behavior**: Explicitly created a CloudEvent and returned metadata.
    -   **New Behavior**: These methods are **removed**. Their core functionality is now the default, transparent behavior of the primary `publish` methods.