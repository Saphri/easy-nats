# Data Model: @NatsSubscriber Annotation

This feature does not introduce any new persistent data models. The primary data element is the `@NatsSubscriber` annotation itself, which is a metadata construct.

## Annotation: `@NatsSubscriber`

-   **`value`**: `String`
    -   **Description**: The NATS subject to which the annotated method will subscribe.
    -   **Constraints**: Must not be an empty string.
