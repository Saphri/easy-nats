# Research: @NatsSubscriber Annotation Implementation

## Decision

The `@NatsSubscriber` annotation will be implemented by discovering annotated methods at build time using a Quarkus `BuildStep`. The `QuarkusEasyNatsProcessor` will scan for methods annotated with `@NatsSubscriber` and generate the necessary CDI beans and configuration to create the NATS subscriptions at runtime.

## Rationale

This approach is consistent with Quarkus's build-time processing philosophy and provides several advantages:

-   **Performance**: Discovering subscribers at build time avoids runtime reflection, which is crucial for fast startup and native compilation.
-   **Validation**: We can validate the annotated methods' signatures and the annotation's attributes at build time, providing developers with early feedback on errors.
-   **Integration**: This approach integrates cleanly with the Quarkus CDI ecosystem, allowing us to manage the lifecycle of the subscribers automatically.

## Alternatives considered

-   **Runtime discovery**: We could have used a CDI extension to discover the annotated methods at runtime. This would be less performant and more complex to implement correctly, especially for native compilation.
-   **Programmatic registration**: We could have required developers to manually register their subscribers in a configuration file or a dedicated service. This would be more verbose and less convenient than using an annotation.
