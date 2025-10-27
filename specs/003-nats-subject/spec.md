# Specification: @NatsSubject Annotation
**Continues**: `002-typed-publisher`

## 1. Overview

This document specifies a new feature for the Quarkus EasyNats extension: a `@NatsSubject` annotation. This annotation will allow developers to inject a `NatsPublisher` instance with a specific NATS subject pre-configured, decoupling the subject from the application logic.

## Clarifications

### Session 2025-10-26

- Q: How should the system behave if the `@NatsSubject` annotation is applied to a type other than `NatsPublisher`? → A: Fail at build time with a clear error message.
- Q: Should a custom exception be created or a standard one used? → A: Use `jakarta.enterprise.inject.spi.DefinitionException`.

## 2. Functional Requirements

- **FR1: Annotation Definition**: A `@NatsSubject` annotation must be defined.
- **FR2: Annotation Value**: The annotation must accept a `value` attribute of type `String` to specify the NATS subject.
- **FR3: Injection Target**: The annotation should be applicable to injection points of type `NatsPublisher`.
- **FR4: Runtime Resolution**: The Quarkus extension must be able to resolve the `@NatsSubject` annotation at runtime.
- **FR5: Producer Method**: A CDI producer method (`@Produces`) must be implemented to create `NatsPublisher` instances based on the annotation.
- **FR6: InjectionPoint Access**: The producer method must use `InjectionPoint` to access the annotation and its value.
- **FR7: Subject Association**: The produced `NatsPublisher` instance must be configured to publish messages to the subject specified in the annotation.

## 3. Non-Functional Requirements

- **NFR1: Performance**: The annotation processing should have minimal impact on application startup time.
- **NFR2: Testability**: The feature must be easily testable using Quarkus test framework.

## 4. User Stories

- As a developer, I want to use an annotation to define the NATS subject for a `NatsPublisher` so that I can keep my subject definitions declarative and separate from my business logic.
- As a developer, I want to inject multiple `NatsPublisher` instances with different subjects in the same bean.

## 5. Edge Cases

- **EC1: Empty Subject**: If the `@NatsSubject` annotation is used with an empty string, a `jakarta.enterprise.inject.spi.DefinitionException` should be thrown at startup.
- **EC2: Missing Annotation**: If `NatsPublisher` is injected without a `@NatsSubject` annotation, it should behave as a generic publisher without a default subject.
- **EC3: Incorrect Type**: If the `@NatsSubject` annotation is applied to a type other than `NatsPublisher`, a `jakarta.enterprise.inject.spi.DefinitionException` should be thrown at build time.