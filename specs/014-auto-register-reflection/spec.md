# Feature Specification: Automatic Reflection Registration for NATS Subscriber Types

## 1. Introduction

This document specifies the requirements for a feature that automatically registers Java types for reflection when used within `@NatsSubscriber` annotated methods. This will improve the developer experience by removing the need for manual `@RegisterForReflection` annotations, which are error-prone and easy to forget, especially for developers new to Quarkus native compilation.

## 2. User Story

**As a Developer**, I would like my type classes to be registered for reflection at build-time automatically, **so that** I don't have to remember to add `@RegisterForReflection` on my classes used in NATS subscribers.

## 3. Scope

### In Scope

-   Automatic detection of types used as parameters in methods annotated with `@NatsSubscriber`.
-   Registration of these detected types for reflection with Quarkus's build process.
-   Support for standard Java types, custom POJOs, and generic types used within subscriber method signatures.
-   For generic collection types (`java.util.List`, `java.util.Set`, `java.util.Queue`, `java.util.Map`), the collection type itself and its immediate generic argument type will be registered.

### Out of Scope

-   Automatic registration for types used with `NatsPublisher`.
-   Registration for types not directly present in the method signature of a `@NatsSubscriber` (e.g., nested types that are not automatically handled by the default reflection registration), beyond the specified generic collection types.

## 4. Functional Requirements

| ID | Requirement |
| :--- | :--- |
| FR-1 | The Quarkus extension must scan for all methods annotated with `@NatsSubscriber` at build time. |
| FR-2 | For each discovered `@NatsSubscriber` method, the extension must identify the Java types of its parameters, including the top-level type and, for `java.util.List`, `java.util.Set`, `java.util.Queue`, and `java.util.Map`, their immediate generic argument types. |
| FR-3 | The extension must automatically register the identified parameter types for reflection with Quarkus. |
| FR-4 | The automatic registration must be sufficient for the types to be correctly serialized/deserialized when running as a native executable. |

## 5. User Scenarios & Testing

### Scenario 1: Developer creates a NATS subscriber for a custom type

-   **Given** a developer has a Quarkus project with the `quarkus-easy-nats` extension.
-   **And** they have a custom POJO class, `CustomMessage`.
-   **When** they create a method `public void receiveMessage(CustomMessage message)` and annotate it with `@NatsSubscriber(subject = "test.subject")`.
-   **And** they build a native executable of the application.
-   **Then** the application should compile successfully without any reflection-related errors for `CustomMessage`.
-   **And** when a message is published to "test.subject", the `receiveMessage` method should be invoked with the correctly deserialized `CustomMessage` object.

## 6. Success Criteria

| ID | Criteria | Metric |
| :--- | :--- | :--- |
| SC-1 | Reduced boilerplate | Developers no longer need to add `@RegisterForReflection` on types consumed by `@NatsSubscriber` methods. |
| SC-2 | Native compilation success | Projects using `@NatsSubscriber` with custom types compile into native executables without reflection configuration errors. |
| SC-3 | Correct runtime behavior | At runtime, messages are correctly deserialized to the expected types in native mode. |

## 7. Assumptions

-   The types requiring reflection are limited to the parameter types of `@NatsSubscriber` methods, as clarified for generic collections.
-   The primary benefit of this feature is for native image compilation, though it may also simplify development in JVM mode.
-   Developers are using a version of Quarkus that supports the build-time extension mechanisms required to implement this feature.

## 8. Clarifications

### Session 2025-11-01

- Q: When a `@NatsSubscriber` method parameter is a generic type (e.g., `List<MyEvent>`) or a type containing other custom types (e.g., `Wrapper<MyEvent>`), how deeply should the extension search for types to register for reflection? → A: Register the top-level type and its immediate generic arguments, specifically for `java.util.List`, `java.util.Set`, `java.util.Queue`, and `java.util.Map`, along with their generic arguments.
