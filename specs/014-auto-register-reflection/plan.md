# Implementation Plan: Automatic Reflection Registration

## 1. Technical Context

This feature will be implemented as a build-time processor within the `deployment` module of the `quarkus-easy-nats` extension. The core logic will involve using the Jandex library, provided by Quarkus, to inspect the application's class index.

-   **Annotation Scanning**: The processor will scan for methods annotated with `org.mjelle.quarkus.easynats.annotations.NatsSubscriber`.
-   **Type Inspection**: For each annotated method, the processor will inspect its parameters to identify the types that need to be registered for reflection.
-   **Reflection Registration**: The identified types will be registered using Quarkus's `ReflectiveClassBuildItem`. This ensures that the types are available for reflection in the native image.

A key challenge is handling various type scenarios, including:
-   Simple POJOs.
-   Generic types (e.g., `List<MyType>`).
-   Types from external libraries.

The implementation must be careful to only register types that are actually used in subscriber methods to avoid unnecessarily bloating the native image with unused reflection metadata.

## 2. Constitution Check

A check against the project's constitution will be performed to ensure the proposed implementation aligns with the project's principles.

| Principle | Adherence | Notes |
| :--- | :--- | :--- |
| **Developer Experience** | **Improves** | This feature directly addresses a major pain point for developers using the extension with native images, removing a manual, error-prone step. |
| **Performance** | **Neutral** | The changes are at build-time and should not impact runtime performance. The native image size might slightly increase due to the added reflection metadata, but this is a necessary trade-off for correctness. |
| **Maintainability** | **Improves** | By centralizing the reflection registration logic, the feature makes the extension easier to maintain and reason about. |
| **Testability** | **Improves** | The logic can be tested with unit tests in the `deployment` module, ensuring that the type discovery and registration work as expected. |

The feature aligns well with the project's constitution.

## 3. Phase 0: Outline & Research

### Research Tasks

-   **Task 1**: Investigate the precise Jandex APIs for finding all methods annotated with a specific annotation.
-   **Task 2**: Determine the correct way to extract parameter types from a Jandex `MethodInfo` object, including handling of generic types.
-   **Task 3**: Confirm the usage of `ReflectiveClassBuildItem` for registering classes for reflection, including which fields and methods need to be reflected.

### Research Findings

The research will be documented in `research.md`. The key findings are expected to be:
-   The `IndexView` object in a build step provides access to the Jandex index.
-   The `index.getAnnotations(DotName.createSimple(NatsSubscriber.class.getName()))` method can be used to find all usages of the annotation.
-   The `AnnotationInstance.target()` method can be used to get the `MethodInfo`.
-   The `method.parameters()` method provides the list of parameter types. For generic types, further inspection of the type signature will be needed.
-   `new ReflectiveClassBuildItem(true, true, true, className)` is the standard way to register a class for full reflection.

## 4. Phase 1: Design & Contracts

### Data Model

No new data models are required for this feature.

### API Contracts

No external API contracts will be created. The "contract" with the user is that their types will be automatically registered for reflection.

### Quickstart Guide

A section will be added to the documentation explaining that reflection registration for `@NatsSubscriber` parameters is automatic. An example will be provided to illustrate the feature.

## 5. Phase 2: Implementation Tasks

The implementation will be broken down into the following tasks, which will be tracked in `tasks.md`.

-   **Task 1**: Create a new build processor class in the `deployment` module.
-   **Task 2**: Add a build step that gets the Jandex `IndexView`.
-   **Task 3**: Implement the logic to find all `@NatsSubscriber` annotations.
-   **Task 4**: Implement the logic to extract parameter types from the annotated methods.
-   **Task 5**: Implement the logic to produce `ReflectiveClassBuildItem`s for the extracted types.
-   **Task 6**: Add unit tests to verify the type extraction and registration for various scenarios (simple types, generic types, etc.).
-   **Task 7**: Update the documentation to reflect the new feature.