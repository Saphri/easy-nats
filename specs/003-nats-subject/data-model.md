# Data Model: @NatsSubject Annotation

This feature does not introduce any new persistent data models. The primary data element is the `@NatsSubject` annotation itself, which is a metadata construct.

## Annotation: `@NatsSubject`

-   **`value`**: `String`
    -   **Description**: The NATS subject to be used by the injected `NatsPublisher`.
    -   **Constraints**: Must not be an empty string.