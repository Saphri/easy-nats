# Tasks: Explicit Ack/Nak Control

**Input**: Design documents from `/specs/009-explicit-ack-nak/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md, data-model.md ✓, contracts/nats-message-interface.md ✓

**Feature**: 009-explicit-ack-nak (Explicit Ack/Nak Control for NATS JetStream)

**Tests**: Feature spec requires validation via integration tests and contract tests. Include test tasks per TDD practice (Constitution Principle III).

**Organization**: Tasks grouped by user story (US1, US2, US3, US4) to enable independent implementation and testing.

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- All tasks include exact file paths per template requirements

## Path Conventions

- **Runtime code**: `runtime/src/main/java/org/mjelle/quarkus/easynats/`
- **Runtime tests**: `runtime/src/test/java/org/mjelle/quarkus/easynats/`
- **Deployment code**: `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/`
- **Integration tests**: `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/`

---

## Phase 1: Setup (Infrastructure & Build Baseline)

**Purpose**: Establish baseline verification that project builds and existing features still work

- [ ] T001 Verify project builds cleanly without new errors: `./mvnw clean install -DskipTests`
- [ ] T002 Verify existing integration tests pass to confirm feature 008-durable-nats-consumers still works: `./mvnw -pl integration-tests clean test`

---

## Phase 2: Foundational (NatsMessage<T> Interface & Type Support)

**Purpose**: Core infrastructure for explicit ack/nak control - BLOCKS all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 [P] Create `NatsMessage<T>` interface in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsMessage.java` with methods: `payload()`, `ack()`, `nak()`, `nakWithDelay(Duration)`, `term()`, `headers()`, `subject()`, `metadata()`
- [ ] T004 [P] Implement `DefaultNatsMessage<T>` class in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/DefaultNatsMessage.java` wrapping io.nats.client.Message.
- [ ] T005 [P] Update `DefaultMessageHandler` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/handler/DefaultMessageHandler.java` to detect `NatsMessage<T>` parameter type and determine explicit mode.
- [ ] T006 [P] Modify invocation logic in `DefaultMessageHandler.java` to create `DefaultNatsMessage<T>` wrapper when in explicit mode.
- [ ] T007 Update exception handling in `DefaultMessageHandler.java` to skip auto-ack/nak when in explicit mode.

**Checkpoint**: NatsMessage<T> infrastructure ready; explicit ack/nak control enabled

---

## Phase 3: User Story 1 - Acknowledge a message after processing completes (Priority: P1) 🎯 MVP

**Goal**: Developers can explicitly acknowledge messages after successful processing using `NatsMessage<T>.ack()`

**Independent Test**: A Quarkus application with `@NatsSubscriber` method receiving `NatsMessage<Order>` can call `ack()` and verify message is marked delivered and not redelivered

### Contract Tests for User Story 1 (TDD - Write First)

- [ ] T008 [P] [US1] Create contract test for `NatsMessage.ack()` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/contracts/NatsMessageAckTest.java`: verify `ack()` marks message delivered at broker. Use `@QuarkusTestResource(NatsStreamTestResource.class)`, `NatsTestUtils` for NATS access, and `purgeStream()` in `@AfterEach`
- [ ] T009 [P] [US1] Create contract test for ack idempotency in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/contracts/NatsMessageAckTest.java`: verify multiple `ack()` calls succeed without error. Follow NatsTestUtils fixture pattern for stream/consumer setup

### Integration Tests for User Story 1 (TDD - Write First)

- [ ] T010 [P] [US1] Create integration test class `AckTest` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/AckTest.java` with `@QuarkusTest` and `@QuarkusTestResource(NatsStreamTestResource.class)`: test subscriber receives `NatsMessage<Order>` parameter. Include `@AfterEach purgeStream()` for test isolation
- [ ] T011 [P] [US1] Add test method in `AckTest.java`: verify message is marked delivered after `ack()` and not redelivered when subscriber restarts. Use `NatsTestUtils.getJetStream()` to verify message state at broker
- [ ] T012 [P] [US1] Add test method in `AckTest.java`: verify `ack()` idempotency - second call does not cause error. Use `await()` from Awaitility for async verification
- [ ] T013 [US1] Create `AckIT` class in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/AckIT.java` extending `AckTest` for native image validation (inherits all test methods and fixture setup)

### Implementation for User Story 1

- [ ] T014 [P] [US1] Create test subscriber bean `AckOrderListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/AckOrderListener.java` with `@NatsSubscriber` method accepting `NatsMessage<Order>` and calling `ack()`
- [ ] T015 [P] [US1] Create REST endpoint in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/AckOrderResource.java` to publish test orders and retrieve results
- [ ] T016 [US1] Run contract tests to verify `ack()` behavior: `./mvnw -pl integration-tests test -Dtest=NatsMessageAckTest`
- [ ] T017 [US1] Run integration tests to verify end-to-end ack workflow: `./mvnw -pl integration-tests test -Dtest=AckTest`
- [ ] T018 [US1] Verify project still builds: `./mvnw clean install -DskipTests`

**Checkpoint**: User Story 1 complete - explicit acknowledgment works end-to-end

---

## Phase 4: User Story 2 - Reject a message with negative acknowledgment (Priority: P1)

**Goal**: Developers can explicitly reject messages and request redelivery using `NatsMessage<T>.nak(Duration)`

**Independent Test**: A Quarkus application with `@NatsSubscriber` method receiving `NatsMessage<Order>` can call `nak(Duration)` and verify message is redelivered after the specified delay

### Contract Tests for User Story 2 (TDD - Write First)

- [ ] T019 [P] [US2] Create contract test for `NatsMessage.nak(Duration)` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/contracts/NatsMessageNakTest.java`: verify `nak()` marks message for redelivery with specified delay. Use `@QuarkusTestResource(NatsStreamTestResource.class)` and `NatsTestUtils.purgeStream()` in `@AfterEach`
- [ ] T020 [P] [US2] Add contract test in `NatsMessageNakTest.java`: verify `nak()` idempotency - multiple calls succeed without error. Validate via `NatsTestUtils.getJetStream().getConsumerInfo()` metadata
- [ ] T021 [P] [US2] Add contract test in `NatsMessageNakTest.java`: verify redelivery count increments after `nak()`. Use `MessageMetadata.redeliveryCount` to validate retry attempts

### Integration Tests for User Story 2 (TDD - Write First)

- [ ] T022 [P] [US2] Create integration test class `NakTest` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NakTest.java` with `@QuarkusTest` and `@QuarkusTestResource(NatsStreamTestResource.class)`. Include `@AfterEach purgeStream()` for test isolation
- [ ] T023 [P] [US2] Add test method in `NakTest.java`: verify message is redelivered after `nak()` with specified delay. Use `await()` to poll for redelivery; validate with `NatsTestUtils.getJetStream()`
- [ ] T024 [P] [US2] Add test method in `NakTest.java`: verify `nak()` idempotency - second call succeeds. Check consumer pending count remains consistent
- [ ] T025 [US2] Create `NakIT` class in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NakIT.java` extending `NakTest` for native image validation (inherits all test methods and fixture setup)

### Implementation for User Story 2

- [ ] T026 [P] [US2] Create test subscriber bean `NakOrderListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/NakOrderListener.java` that calls `nak()` on error conditions
- [ ] T027 [P] [US2] Create REST endpoint in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/NakOrderResource.java` to trigger errors and retrieve redelivery metrics
- [ ] T028 [US2] Run contract tests to verify `nak()` behavior: `./mvnw -pl integration-tests test -Dtest=NatsMessageNakTest`
- [ ] T029 [US2] Run integration tests to verify end-to-end nak workflow: `./mvnw -pl integration-tests test -Dtest=NakTest`
- [ ] T030 [US2] Verify project still builds with US2 changes: `./mvnw clean install -DskipTests`

**Checkpoint**: User Story 2 complete - explicit negative acknowledgment with redelivery works end-to-end

---

## Phase 5: User Story 3 - Disable automatic acknowledgment for manual control (Priority: P1)

**Goal**: Developers can use parameter type to control automatic vs. explicit ack/nak. Using `NatsMessage<T>` parameter disables auto-ack (explicit mode); using typed payload only enables auto-ack (implicit mode)

**Independent Test**: A Quarkus application with both `@NatsSubscriber` methods (explicit and implicit modes) can verify automatic acknowledgment behavior matches parameter type

### Contract Tests for User Story 3 (TDD - Write First)

- [ ] T031 [P] [US3] Create contract test in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/contracts/ExplicitImplicitModeTest.java`: verify messages stay pending when parameter is `NatsMessage<T>` and method completes without calling `ack()`. Use `@QuarkusTestResource(NatsStreamTestResource.class)` and `NatsTestUtils.getJetStream().getConsumerInfo()` to verify pending count
- [ ] T032 [P] [US3] Add contract test in `ExplicitImplicitModeTest.java`: verify messages ARE automatically acknowledged when parameter is typed payload (e.g., `Order`) and method completes successfully. Use `purgeStream()` between tests for isolation
- [ ] T033 [P] [US3] Add contract test in `ExplicitImplicitModeTest.java`: verify automatic nak occurs when method throws exception with typed payload parameter. Verify redelivery via `await()` and consumer metadata

### Integration Tests for User Story 3 (TDD - Write First)

- [ ] T034 [P] [US3] Create integration test class `ModeDetectionTest` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/ModeDetectionTest.java` with `@QuarkusTest` and `@QuarkusTestResource(NatsStreamTestResource.class)`. Include `@AfterEach purgeStream()` for isolation
- [ ] T035 [P] [US3] Add test method in `ModeDetectionTest.java`: verify messages remain pending when parameter is `NatsMessage<T>` and developer does not call ack/nak. Check via `NatsTestUtils.getJetStream().getConsumerInfo().getNumPending()`
- [ ] T036 [P] [US3] Add test method in `ModeDetectionTest.java`: verify automatic ack behavior with typed-payload-only parameter (implicit mode). Poll with `await()` to verify message disappears from pending
- [ ] T037 [P] [US3] Add test method in `ModeDetectionTest.java`: verify automatic nak behavior when subscriber method throws exception (implicit mode). Validate redelivery count increments
- [ ] T038 [US3] Create `ModeDetectionIT` class in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/ModeDetectionIT.java` extending `ModeDetectionTest` for native image validation (inherits all test methods and fixture setup)

### Implementation for User Story 3

- [ ] T039 [P] [US3] Create test bean `ExplicitModeListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/ExplicitModeListener.java` with `@NatsSubscriber` method using `NatsMessage<Order>` parameter (explicit mode)
- [ ] T040 [P] [US3] Create test bean `ImplicitModeListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/ImplicitModeListener.java` with `@NatsSubscriber` method using `Order` parameter (implicit mode with auto-ack)
- [ ] T041 [P] [US3] Create REST endpoints in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/ModeDetectionResource.java` to test both explicit and implicit mode subscribers
- [ ] T042 [US3] Run contract tests to verify mode detection: `./mvnw -pl integration-tests test -Dtest=ExplicitImplicitModeTest`
- [ ] T043 [US3] Run integration tests to verify mode detection: `./mvnw -pl integration-tests test -Dtest=ModeDetectionTest`
- [ ] T044 [US3] Verify project still builds: `./mvnw clean install -DskipTests`

**Checkpoint**: User Story 3 complete - parameter type detection and mode selection work correctly

---

## Phase 6: User Story 4 - Use ack/nak in different error handling scenarios (Priority: P2)

**Goal**: Developers can implement complex error handling workflows using conditional ack/nak logic to distinguish transient vs. permanent errors

**Independent Test**: A Quarkus application with `@NatsSubscriber` method can handle different error types and route messages correctly (transient → nak, permanent → ack with logging)

### Integration Tests for User Story 4 (TDD - Write First)

- [ ] T045 [P] [US4] Create integration test class `ErrorHandlingTest` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/ErrorHandlingTest.java` with `@QuarkusTest` and `@QuarkusTestResource(NatsStreamTestResource.class)`. Include `@AfterEach purgeStream()` for test isolation
- [ ] T046 [P] [US4] Add test method in `ErrorHandlingTest.java`: subscriber handles transient error with `nak()` for retry. Use `await()` to verify redelivery; check `NatsTestUtils.getJetStream().getConsumerInfo()` for pending messages
- [ ] T047 [P] [US4] Add test method in `ErrorHandlingTest.java`: subscriber handles permanent error with `ack()` and logging. Verify message is NOT redelivered after ack despite error
- [ ] T048 [P] [US4] Add test method in `ErrorHandlingTest.java`: subscriber implements exponential backoff using `redeliveryCount` and `nak(Duration)`. Track redelivery count via message metadata; verify delays increase
- [ ] T049 [P] [US4] Add test method in `ErrorHandlingTest.java`: subscriber implements dead-letter queue pattern (max retries exceeded). Verify message moves to DLQ after max attempts; confirm no further redelivery
- [ ] T050 [US4] Create `ErrorHandlingIT` class in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/ErrorHandlingIT.java` extending `ErrorHandlingTest` for native image validation (inherits all test methods and fixture setup)

### Implementation for User Story 4

- [ ] T051 [P] [US4] Create custom exception classes in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/error/` for test scenarios: `TransientException.java`, `PermanentException.java`
- [ ] T052 [P] [US4] Create test subscriber bean `ConditionalAckNakListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/ConditionalAckNakListener.java` with error type discrimination
- [ ] T053 [P] [US4] Create test subscriber bean `ExponentialBackoffListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/ExponentialBackoffListener.java` implementing retry with backoff
- [ ] T054 [P] [US4] Create test subscriber bean `DeadLetterListener` in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/DeadLetterListener.java` implementing dead-letter queue pattern
- [ ] T055 [US4] Create REST endpoints in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/ErrorHandlingResource.java` to trigger different error conditions
- [ ] T056 [US4] Run integration tests to verify error handling scenarios: `./mvnw -pl integration-tests test -Dtest=ErrorHandlingTest`
- [ ] T057 [US4] Verify project builds: `./mvnw clean install -DskipTests`

**Checkpoint**: User Story 4 complete - complex error handling patterns work correctly

---

## Phase 7: Unit Tests & Coverage (Runtime Module)

**Purpose**: Ensure >80% code coverage for runtime module per Constitution Principle III (TDD)

- [ ] T058 [P] Create unit test class `NatsMessageImplTest` in `runtime/src/test/java/org/mjelle/quarkus/easynats/runtime/message/NatsMessageImplTest.java`: test payload deserialization and all control methods
- [ ] T059 [P] Create unit test class `SubscriberMethodRegistryTest` in `runtime/src/test/java/org/mjelle/quarkus/easynats/runtime/subscriber/SubscriberMethodRegistryTest.java`: test detection of `NatsMessage<T>` parameter and explicit mode determination
- [ ] T060 [P] Create unit test class `SubscriberInvokerTest` in `runtime/src/test/java/org/mjelle/quarkus/easynats/runtime/subscriber/SubscriberInvokerTest.java`: test explicit vs. implicit ack/nak invocation
- [ ] T061 Run unit tests and verify coverage >80%: `./mvnw -pl runtime clean test`

---

## Phase 8: Documentation & Validation

**Purpose**: Complete documentation and validate quickstart examples

- [ ] T062 [P] Update javadoc in `NatsMessage.java` interface with complete method descriptions and usage examples
- [ ] T063 [P] Update `CLAUDE.md` project guidelines with explicit ack/nak best practices
- [ ] T064 Add section to feature spec documenting known limitations and async safety considerations
- [ ] T065 Validate all quickstart.md examples compile and execute: Review examples in `quickstart.md` against implementation
- [ ] T066 Verify contract test assumptions in `contracts/nats-message-interface.md` match implementation

**Checkpoint**: Documentation complete and validated

---

## Phase 9: Integration & Final Validation

**Purpose**: Cross-cutting validation and final quality gates

- [ ] T067 [P] Run full test suite with coverage: `./mvnw clean install -Pit`
- [ ] T068 [P] Run native image tests (if GraalVM available): `./mvnw clean install -Pit`
- [ ] T069 Verify all four user stories work together: Create integration test combining US1, US2, US3, US4 scenarios
- [ ] T070 Run quickstart validation: Confirm all code examples from `quickstart.md` and `contracts/` work as documented
- [ ] T071 Final build verification: `./mvnw clean install`

**Checkpoint**: All tests pass; feature complete; ready for merge

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories ✅
- **User Stories (Phase 3-6)**: All depend on Foundational phase (Phase 2) completion
  - US1, US2, US3 (P1) can proceed in parallel after Foundational
  - US4 (P2) can start after any P1 story completes (depends on framework baseline)
- **Unit Tests (Phase 7)**: Depends on foundational + all user story implementations
- **Documentation (Phase 8)**: Can start after Phase 2 but finalized after Phase 6
- **Integration & Final (Phase 9)**: Depends on all previous phases

### User Story Dependencies

- **User Story 1 (P1 - Ack)**: Can start after Foundational (Phase 2) ✅
- **User Story 2 (P1 - Nak)**: Can start after Foundational (Phase 2) ✅
- **User Story 3 (P1 - Mode Detection)**: Can start after Foundational (Phase 2) ✅
- **User Story 4 (P2 - Complex Error Handling)**: Can start after Foundational (Phase 2); benefits from US1-US3 knowledge but independent

### Parallel Opportunities

- **Phase 1**: Both tasks can run sequentially (quick verification)
- **Phase 2**: All foundational tasks marked [P] can run in parallel:
  - T003, T004, T005, T006 are independent file creation/modification tasks
  - T007 depends on T005/T006 understanding mode detection logic
- **Phase 3-6**: Within each user story:
  - All contract tests marked [P] can run in parallel
  - All integration tests marked [P] can run in parallel
  - All implementation tasks marked [P] can run in parallel
  - Different user stories can be worked on in parallel by different developers
- **Phase 7**: All unit test tasks marked [P] can run in parallel
- **Phase 8**: All documentation tasks marked [P] can run in parallel

### Example Parallel Execution (With Multiple Developers)

```
Phase 1 (Dev Lead): Verify builds
        ↓
Phase 2 (Entire Team): Foundational infrastructure
  - Dev A: T003 (NatsMessage interface)
  - Dev B: T004 (NatsMessage implementation)
  - Dev C: T005 (SubscriberMethodRegistry update)
  - Dev D: T006 (SubscriberInvoker updates)
        ↓
Phase 3-6 (Parallel by Developer):
  - Dev A: User Story 1 (Ack) - T008-T018
  - Dev B: User Story 2 (Nak) - T019-T030
  - Dev C: User Story 3 (Mode Detection) - T031-T044
  - Dev D: User Story 4 (Error Handling) - T045-T057
        ↓
Phase 7 (Entire Team - Parallel):
  - Dev A: T058 (NatsMessage unit tests)
  - Dev B: T059 (SubscriberMethodRegistry unit tests)
  - Dev C: T060 (SubscriberInvoker unit tests)
        ↓
Phase 8 (Entire Team - Parallel):
  - Dev A: T062 (Javadoc)
  - Dev B: T063 (CLAUDE.md updates)
  - Dev C: T064 (Spec updates)
        ↓
Phase 9 (Entire Team - Sequential):
  - All: Verify tests, native image, final build
```

---

## Implementation Strategy

### MVP First (User Stories 1-3 Only - All P1)

1. Complete Phase 1: Setup (verify baseline)
2. Complete Phase 2: Foundational (NatsMessage infrastructure)
3. Complete Phase 3: User Story 1 (Ack) - Core functionality ✅
4. Complete Phase 4: User Story 2 (Nak) - Redelivery control ✅
5. Complete Phase 5: User Story 3 (Mode Detection) - Parameter type control ✅
6. **STOP and VALIDATE**: All P1 stories work independently and together
7. Deploy/demo MVP with explicit ack/nak control

### Incremental Delivery (Add P2 later)

After MVP validates:

8. Complete Phase 6: User Story 4 (Error Handling) - Advanced patterns
9. Complete Phase 7: Unit Tests - Coverage validation
10. Complete Phase 8: Documentation - Final polish
11. Complete Phase 9: Final validation - Ship feature

### Minimal Viable Feature Scope

- [x] NatsMessage<T> interface with ack/nak/term
- [x] Automatic vs. explicit control via parameter type
- [x] Integration tests proving ack/nak/term work
- [x] Quickstart documentation
- [ ] Advanced error handling patterns (Phase 6 - nice-to-have)
- [ ] Full unit test coverage (Phase 7 - nice-to-have)

---

## Notes

- **[P] marker**: Tasks with [P] can run in parallel (different files, no blocking dependencies)
- **[Story] label**: Maps task to specific user story (US1, US2, US3, US4) for traceability
- **TDD Approach**: Contract + integration tests written FIRST (Phases 3-6), then implementation
- **Parameter Type Detection**: All automatic vs. explicit control determined by parameter type ONLY (no annotation properties). Framework detects `NatsMessage<T>` at annotation processing time (Phase 2 foundational tasks T005-T007)
- **Independent Testability**: Each user story can be tested independently; no hard dependencies between stories
- **Test Fixture Pattern** (CRITICAL for local NATS):
  - All test classes MUST use `@QuarkusTestResource(NatsStreamTestResource.class)` for stream/consumer setup
  - All test classes MUST include `@AfterEach` method calling `NatsTestUtils.purgeStream()` for message isolation
  - All NATS access MUST use `NatsTestUtils.getJetStream()` (not direct connections)
  - Tests MUST use `NatsTestUtils.STREAM_NAME` and `DURABLE_CONSUMER_NAME` constants (no hardcoded values)
  - Use `await()` from Awaitility for async verification (never `Thread.sleep()`)
  - This pattern prevents test pollution on the shared local NATS instance
- **Constitution Compliance**:
  - Principle III (TDD): Tests included in all phases; >80% coverage target (Phase 7)
  - Principle I (Extension-First): NatsMessage added to runtime module; no deployment changes
  - Principle VI (Developer Experience): Parameter type alone determines ack/nak control (no boilerplate)
- **Commit Points**: Suggest commits after each user story phase (after T018, T030, T044, T057) to maintain atomic history
- **Test First**: All integration tests marked as TDD - write tests, verify FAIL, then implement
- **No Validation**: Framework does not validate AckPolicy; NATS rejects invalid configs at runtime (per spec)

---

## Summary Statistics

- **Total Tasks**: 71 (T001-T071)
- **Setup Phase**: 2 tasks
- **Foundational Phase**: 5 tasks
- **User Story 1 (P1)**: 11 tasks (8 tests + 3 impl)
- **User Story 2 (P1)**: 12 tasks (8 tests + 4 impl)
- **User Story 3 (P1)**: 14 tasks (11 tests + 3 impl) *[Fixed: removed 2 autoAck property tasks]*
- **User Story 4 (P2)**: 13 tasks (5 tests + 8 impl)
- **Unit Tests**: 4 tasks
- **Documentation**: 5 tasks
- **Integration**: 5 tasks
- **Estimated Duration**: 2-3 weeks for full feature (can be shortened to 1 week for P1 MVP)
- **Parallel Opportunities**: 40+ tasks can run in parallel (marked [P])
- **MVP Deliverable**: Phase 1-5 (US1-US3) = ~48 tasks, 1-2 weeks
