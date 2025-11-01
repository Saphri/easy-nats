# Research: Automatic Reflection Registration

## Decision

The implementation will use a Quarkus build-time processor to scan for `@NatsSubscriber` annotations and register the parameter types for reflection. The Jandex library will be used for annotation scanning and type inspection.

## Rationale

This approach is the standard and most efficient way to implement build-time features in Quarkus. It integrates directly with the Quarkus build process, allowing the extension to analyze the application's code and contribute to the build output (in this case, by adding reflection metadata). Using Jandex is the recommended way to inspect class information at build time in Quarkus.

## Alternatives Considered

-   **Manual Registration**: This is the current approach, which this feature aims to replace. It is error-prone and adds boilerplate code.
-   **Runtime Scanning**: Scanning for annotations at runtime would be too slow and is not compatible with native image compilation, which requires all reflection to be configured at build time.
