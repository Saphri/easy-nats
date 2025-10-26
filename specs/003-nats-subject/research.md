# Research: @NatsSubject Annotation Implementation

## Decision

The `@NatsSubject` annotation will be implemented using a CDI `@Produces` method in the `NatsPublisherRecorder` class. This method will use `jakarta.enterprise.inject.spi.InjectionPoint` to access the annotation and its value at runtime.

## Rationale

The user's suggestion to use `@Produces` and `InjectionPoint` is the correct and standard way to implement this kind of feature in a CDI-based framework like Quarkus.

- **`@Produces`**: This CDI annotation marks a method as a producer of beans. In our case, it will produce `NatsPublisher` instances.
- **`InjectionPoint`**: This CDI interface provides metadata about an injection point, such as the annotations present on it. We can use it to get the `@NatsSubject` annotation and its `value`.

This approach allows for a clean separation of concerns:
- The `@NatsSubject` annotation is a simple declaration of the subject.
- The producer method in `NatsPublisherRecorder` contains the logic for creating and configuring the `NatsPublisher`.
- The `QuarkusEasyNatsProcessor` will be used to discover all `@NatsSubject` injection points and register the necessary beans.

## Alternatives considered

- **Build-time code generation**: We could have used bytecode generation (e.g., with Gizmo) to create subclasses of `NatsPublisher` with the subject hardcoded. This would be more complex to implement and maintain.
- **Manual configuration**: We could have required developers to manually configure each `NatsPublisher` with a subject in `application.properties`. This would be verbose and less convenient than using an annotation.