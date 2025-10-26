# Tasks: @NatsSubject Annotation

This document outlines the tasks required to implement the `@NatsSubject` annotation feature.

## Phase 1: Foundational

- [ ] T001 Create the `@NatsSubject` annotation in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsSubject.java`

## Phase 2: User Story 1 - Declarative Subject Definition

**Goal**: As a developer, I want to use an annotation to define the NATS subject for a `NatsPublisher` so that I can keep my subject definitions declarative and separate from my business logic.

**Independent Test Criteria**: An integration test can inject a `NatsPublisher` using `@NatsSubject`, publish a message, and a NATS consumer can receive the message on the correct subject.

- [x] T002 [US1] Create a new integration test file `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NatsSubjectTest.java`
- [x] T003 [US1] Write a failing test in `NatsSubjectTest.java` that injects a `@NatsSubject("test-subject") NatsPublisher` and fails to receive a message.
- [x] T004 [US1] Implement the CDI producer method for `NatsPublisher` in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisherRecorder.java`
- [x] T005 [US1] Implement the build step in `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/QuarkusEasyNatsProcessor.java` to discover `@NatsSubject` injection points.
- [x] T006 [US1] Run the integration test and confirm it passes.

## Phase 3: User Story 2 - Multiple Publishers

**Goal**: As a developer, I want to inject multiple `NatsPublisher` instances with different subjects in the same bean.

**Independent Test Criteria**: The integration test can inject two `NatsPublisher` instances with different `@NatsSubject` annotations, publish messages to both, and consumers receive the messages on the correct, distinct subjects.

- [x] T007 [US2] Write a failing test in `NatsSubjectIntegrationTest.java` that injects two `NatsPublisher`s with different subjects and asserts that messages are received on the correct subjects.
- [x] T008 [US2] Run the integration test and confirm it passes. (No new production code should be necessary).

## Phase 4: Polish & Cross-Cutting Concerns

- [x] T009 Implement validation for empty subject string in `QuarkusEasyNatsProcessor.java` to throw a `DefinitionException`.
- [x] T010 Implement validation for incorrect injection type in `QuarkusEasyNatsProcessor.java` to throw a `DefinitionException`.
- [x] T011 [P] Add Javadoc to the `@NatsSubject` annotation.
- [x] T012 [P] Add Javadoc to the new public methods in `NatsPublisherRecorder.java` and `QuarkusEasyNatsProcessor.java`.

## Dependencies

- User Story 1 (Phase 2) must be completed before User Story 2 (Phase 3).
- Polish (Phase 4) can be done after Phase 3.

## Parallel Execution

- Within Phase 4, T011 and T012 can be executed in parallel.

## Implementation Strategy

The implementation will follow a Test-Driven Development (TDD) approach. The MVP is the completion of User Story 1. User Story 2 is a fast-follow that validates the robustness of the initial implementation.