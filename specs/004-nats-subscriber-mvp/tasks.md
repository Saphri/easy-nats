---
description: "Task list for implementing @NatsSubscriber annotation MVP"
---

# Tasks: @NatsSubscriber Annotation (MVP)

**Input**: Design documents from `/specs/004-nats-subscriber-mvp/`
**Branch**: `004-nats-subscriber-mvp`
**Status**: Ready for Implementation
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓, quickstart.md ✓

**Tests**: Integration tests included (per feature specification: SC-001, SC-002, SC-003, SC-004)

**Organization**: Tasks grouped by user story (US1) to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and annotation interface definition

- [x] T001 Create `@NatsSubscriber` annotation in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsSubscriber.java` with `@Retention(RetentionPolicy.RUNTIME)` and `@Target(ElementType.METHOD)`, including `String value()` attribute
- [x] T002 Verify annotation is properly exported in the runtime module's public API

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core build-time and runtime infrastructure for subscriber discovery and message handling

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Implement `SubscriberDiscoveryProcessor` in `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/processor/SubscriberDiscoveryProcessor.java` to scan for `@NatsSubscriber` annotations at build time using Jandex
- [x] T004 Add build-time validation logic in `SubscriberDiscoveryProcessor` to:
  - Reject methods with non-String parameter types (throw `DefinitionException`)
  - Reject empty/null subject values (throw `DefinitionException`)
  - Validate exactly one parameter per annotated method
- [x] T005 Create `SubscriberMetadata` record in `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/metadata/SubscriberMetadata.java` to hold build-time information: subject, method class, method name, declaring bean class
- [x] T006 Create `SubscriberBuildItem` in `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/build/SubscriberBuildItem.java` to pass subscriber metadata through Quarkus build pipeline
- [x] T007 Integrate `SubscriberDiscoveryProcessor` into `QuarkusEasyNatsProcessor` as a new `@BuildStep` that:
  - Calls `SubscriberDiscoveryProcessor.discoverSubscribers()`
  - Produces `SubscriberBuildItem` instances
  - Registers validation errors with Quarkus build system
- [x] T008 Create `SubscriberRegistry` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/SubscriberRegistry.java` (CDI `@Singleton`) to hold runtime subscriber configuration and initialization logic
- [x] T009 Create `MessageHandler` interface/class in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/handler/MessageHandler.java` to wrap subscriber method invocation with ack/nak logic
- [x] T010 Implement `DefaultMessageHandler` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/handler/DefaultMessageHandler.java` to:
  - Invoke the subscriber method with message payload
  - Handle successful execution: call message.ack()
  - Handle exceptions: call message.nak() and log at ERROR level

**Checkpoint**: Foundation ready - User Story 1 implementation can now begin

---

## Phase 3: User Story 1 - Consume String Messages from a NATS Subject (Priority: P1) 🎯 MVP

**Goal**: Implement the core subscriber feature allowing developers to consume String messages from NATS subjects via `@NatsSubscriber` annotation. This includes ephemeral consumer creation, message delivery, and implicit acknowledgment.

**Independent Test**: Create a minimal Quarkus app with the extension, annotate a method with `@NatsSubscriber("test-subject")`, publish a string message to "test-subject", and verify the annotated method is invoked with the correct payload.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T011 [P] [US1] Contract/API test: Verify `@NatsSubscriber` annotation can be applied to methods in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/AnnotationContractTest.java`
- [x] T012 [P] [US1] Validation test: Verify build fails when annotated method has no parameters in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/ValidationTest.java` (scenario: SC-002)
- [x] T013 [P] [US1] Validation test: Verify build fails when subject is empty/null in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/ValidationTest.java` (scenario: SC-003)
- [x] T014 [US1] Integration test: Basic message consumption - publish string to subject, verify method invoked with payload in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NatsSubscriberTest.java` (scenario: SC-001, FR-001 through FR-005)
- [x] T015 [US1] Integration test: Success acknowledgment - verify ack called on successful method execution in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NatsSubscriberTest.java` (FR-006)
- [x] T016 [US1] Integration test: Failure acknowledgment - verify nak called when method throws exception in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NatsSubscriberTest.java` (FR-007)
- [x] T017 [US1] Integration test: Multiple subscribers - verify multiple annotated methods in same class each create separate consumers in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NatsSubscriberTest.java` (FR-008)
- [x] T018 [US1] Integration test: Startup validation - verify application startup fails with clear error if any subscription fails in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/StartupValidationTest.java` (FR-009)

### Implementation for User Story 1

- [x] T019 [US1] Implement `EphemeralConsumerFactory` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/consumer/EphemeralConsumerFactory.java` to create ephemeral NATS consumers without specifying consumer name (let server assign)
- [x] T020 [US1] Create `SubscriberInitializer` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/startup/SubscriberInitializer.java` (CDI `@Singleton`) that:
  - Depends on `SubscriberRegistry` and `NatsConnectionManager`
  - Listens for application startup event
  - Creates ephemeral consumers for each subscriber
  - Registers message handlers via `consumerContext.consume(handler)`
  - Throws exception on startup if any consumer creation fails
- [x] T021 [US1] Implement subscriber method invocation in `DefaultMessageHandler`:
  - Extract String payload from NATS Message
  - Use reflection to invoke the subscriber method with payload
  - Pass method invocation results to ack/nak logic
- [x] T022 [US1] Add INFO-level logging to `SubscriberInitializer` for subscription creation events (subject and method name) per NFR-001
- [x] T023 [US1] Add ERROR-level logging to `DefaultMessageHandler` for message processing failures (subject, method name, exception stack trace) per NFR-002
- [x] T024 [US1] Verify NO payload logging in error messages to protect sensitive data per NFR-003
- [x] T025 [US1] Wire up `SubscriberBuildItem` instances in `QuarkusEasyNatsProcessor` to populate `SubscriberRegistry` during build
- [x] T026 [US1] Create helper methods in `SubscriberDiscoveryProcessor` to extract and validate method signatures (exactly 1 String parameter)
- [x] T027 [US1] Add logging in `SubscriberDiscoveryProcessor` for build-time subscriber discovery results
- [x] T028 [US1] Run integration tests for User Story 1 with `-Pit` profile and verify all pass with >= 80% code coverage

**Checkpoint**: User Story 1 is complete - all core subscriber functionality is working and independently testable

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Improvements affecting overall quality and user experience

- [ ] T029 [P] Run full test suite with coverage report: `mvn clean install -Pit`
- [ ] T030 [P] Update runtime module's public API documentation (Javadoc for `@NatsSubscriber` annotation)
- [ ] T031 [P] Update deployment module's internal documentation (Javadoc for `SubscriberDiscoveryProcessor`, build items)
- [ ] T032 Review CLAUDE.md guidelines compliance:
  - Constructor injection used (no `@Inject` fields in production code)
  - AssertJ used for all test assertions (no JUnit assertions)
  - Awaitility used for async test scenarios (no `Thread.sleep()`)
- [ ] T033 Validate quickstart.md example works end-to-end in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/QuickstartValidationTest.java`
- [ ] T034 [P] Code cleanup and refactoring based on test feedback
- [ ] T035 Performance validation: Verify minimal impact on application startup time
- [ ] T036 Verify native compilation compatibility (GraalVM native image)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately ✓
- **Foundational (Phase 2)**: Depends on Setup completion (Phase 1) - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational phase (Phase 2) completion
- **Polish (Phase 4)**: Depends on User Story 1 completion

### Within User Story 1

1. **Tests FIRST** (T011-T018): Write and verify they fail before implementation
2. **Foundation** (T019-T020): Ephemeral consumer factory and startup initializer
3. **Core Logic** (T021-T027): Message handling, invocation, logging
4. **Validation** (T028): Run all tests and verify coverage
5. **Polish** (T029-T036): Final improvements and documentation

### Parallel Opportunities

**Phase 1 (Setup)**:
- T001 and T002 are sequential (T002 depends on T001)

**Phase 2 (Foundational)**:
- T003-T007 can overlap (T007 integrates results of T003-T006)
- T008-T010 can run in parallel (independent components)
- Sequential order: T003→T004, T005-T006 parallel, T007 after T003-T006, T008-T010 parallel after T007

**Phase 3 (Tests)**:
- T011-T013 can run in parallel (different test scenarios)
- T014-T018 can run in parallel (different test classes)

**Phase 3 (Implementation)**:
- T019-T020 parallel (independent components)
- T021-T027 mostly sequential (each builds on previous understanding)
- T028 waits for all implementation complete

**Phase 4 (Polish)**:
- T029-T031 can run in parallel
- T032 validates coding standards
- T033-T036 parallel (different concerns)

---

## Parallel Example: User Story 1 Execution

```
PHASE 2 FOUNDATION - Sequential critical path:
  → T003 (discover with Jandex)
  → T004 (add validation to T003)
  → [T005, T006] (parallel: metadata and build item)
  → T007 (integrate into processor)
  → [T008, T009, T010] (parallel: registry, message handler)

PHASE 3 TESTS - Parallel test writing (all fail initially):
  → [T011, T012, T013] (parallel validation tests)
  → [T014, T015, T016, T017, T018] (parallel functional tests)

PHASE 3 IMPLEMENTATION - Critical path with parallelism:
  → [T019, T020] (parallel: factory and initializer)
  → T021 (method invocation in handler)
  → [T022, T023, T024] (parallel: logging)
  → T025 (wire up build items)
  → T026 (helper methods)
  → T027 (discovery logging)
  → T028 (validate all tests pass)

PHASE 4 POLISH - Mix of parallel and sequential:
  → [T029, T030, T031] (parallel: tests, docs)
  → T032 (validate guidelines)
  → [T033, T034, T035, T036] (parallel: quickstart, cleanup, perf, native)
```

---

## Implementation Strategy

### MVP-Only Implementation (Recommended)

1. **Setup Phase (Phase 1)**: Define the annotation
2. **Foundation Phase (Phase 2)**: Build the discovery and handler infrastructure (CRITICAL)
3. **User Story 1 (Phase 3)**: Implement ephemeral subscriber with String payloads
4. **STOP and VALIDATE**: Run all tests, verify >= 80% coverage, test quickstart example
5. **Polish Phase (Phase 4)**: Documentation, guidelines compliance, native image validation
6. **Done**: MVP complete and ready for use

### Testing Strategy

- **Write tests first** (T011-T018) before implementation
- **Verify tests fail** on initial run (no implementation yet)
- **Implement to make tests pass** (T019-T027)
- **Run full suite** with integration tests enabled (`-Pit`)
- **Validate coverage** >= 80% per SC-004

### Key Checkpoints

- **After Phase 1**: Annotation is defined and exported
- **After Phase 2**: Foundation complete; ready for subscriber implementation
- **After Phase 3 Tests**: All test classes created and fail (TDD approach)
- **After Phase 3 Implementation**: All tests pass; User Story 1 fully functional
- **After Phase 4**: MVP complete, documented, and validated

---

## Task Summary

- **Total Tasks**: 36
- **Setup Tasks (Phase 1)**: 2
- **Foundational Tasks (Phase 2)**: 8
- **User Story 1 - Tests (Phase 3)**: 8
- **User Story 1 - Implementation (Phase 3)**: 10
- **Polish Tasks (Phase 4)**: 8

---

## Notes

- [P] tasks = different files, no inter-task dependencies
- [US1] label identifies tasks for User Story 1
- Each task has specific file path for clarity
- Tests written FIRST per TDD approach from CLAUDE.md
- Build-time validation uses Jandex for performance (native image compatibility)
- All logging respects CLAUDE.md: INFO for creation, ERROR for failures, no payload logging
- Message acknowledgment is implicit (ack on success, nak on exception)
- Startup fails immediately if any consumer creation fails (fail-fast approach)
- Implementation uses ConsumerContext API (modern, non-deprecated NATS JetStream API)
