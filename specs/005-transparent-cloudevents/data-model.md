# Data Model: Transparent CloudEvent Publisher

This feature does not introduce any new persistent data models or entities.

The primary focus is on the **transport contract** of messages, ensuring they conform to the CloudEvents specification. The data model of the payload itself remains the responsibility of the application developer (e.g., the `User` or `Product` POJO).

The key data structure involved is the **CloudEvent**, which is mapped to NATS messages as follows:

-   **Attributes** (e.g., `id`, `source`, `type`): Stored as NATS message headers with a `ce-` prefix.
-   **Data** (the payload): Stored as the NATS message body.