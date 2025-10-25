---
description: "Task list for Basic NatsPublisher API (MVP) feature implementation"
---

# Tasks: Basic NatsPublisher API (MVP)

**Input**: Design documents from `/specs/001-basic-publisher-api/`
**Prerequisites**: plan.md (required), spec.md (required), data-model.md, research.md
**Branch**: `001-basic-publisher-api`

**Tests**: TDD approach - tests written FIRST, then implementation. All acceptance tests required per Principle III.

**Organization**: Single user story (P1) with foundational setup tasks and independent test scenarios.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story label (US1 for this MVP, no others)
- Include exact file paths in descriptions
- All file paths are relative to repository root

## Path Conventions

- **runtime**: `runtime/src/main/java/io/quarkus/easynats/`
- **deployment**: `deployment/src/main/java/io/quarkus/easynats/deployment/`
- **integration-tests**: `integration-tests/src/main/java/io/quarkus/easynats/it/`
- **Resources**: `integration-tests/docker-compose-devservices.yml` (existing, reused)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and extension framework setup

**Tasks**:

- [ ] T001 Review and validate project structure per plan.md (multi-module Maven: runtime/deployment/integration-tests)
- [ ] T002 Verify Maven pom.xml includes io.nats:jnats dependency (version per parent pom.xml)
- [ ] T003 [P] Verify quarkus-extension.yaml exists in `runtime/src/main/resources/META-INF/quarkus-extension.yaml`
- [ ] T004 [P] Verify NATS CLI is installed (`nats --version` succeeds)
- [ ] T005 Start docker-compose NATS broker: `docker-compose -f integration-tests/docker-compose-devservices.yml up -d`
- [ ] T006 Create JetStream stream using NATS CLI: `nats stream add test_stream --subjects test --discard old --max-age=-1 --replicas=1`
- [ ] T007 Verify stream created: `nats stream list` should show `test_stream`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core extension infrastructure that blocks all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

**Tasks**:

- [ ] T008 Implement `NatsConnectionManager.java` in `runtime/src/main/java/io/quarkus/easynats/`
  - `@Singleton` Arc bean
  - `@Observes StartupEvent` to initialize NATS connection
  - `getJetStream()` returns shared JetStream instance
  - Connection URL hardcoded to `nats://localhost:4222`
  - Credentials hardcoded to guest/guest
  - Fail-fast: throw exception if connection fails at startup
  - See data-model.md for full API specification

- [ ] T009 Implement `NatsPublisher.java` in `runtime/src/main/java/io/quarkus/easynats/`
  - `public void publish(String message) throws Exception`
  - Injects `NatsConnectionManager` (Arc dependency injection)
  - Publishes message to hardcoded subject `test`
  - Message is raw UTF-8 bytes, no CloudEvents wrapping
  - Throws exceptions if publication fails
  - See data-model.md for full API specification

- [ ] T010 Create deployment processor `QuarkusEasyNatsProcessor.java` in `deployment/src/main/java/io/quarkus/easynats/deployment/`
  - `@BuildStep` method to register NatsPublisher bean for CDI
  - Uses Quarkus build-time processing
  - Registers `NatsConnectionManager` as singleton
  - Registers `NatsPublisher` as injectable bean
  - See plan.md project structure for context

- [ ] T011 [P] Create `NatsFeature.java` in `deployment/src/main/java/io/quarkus/easynats/deployment/`
  - Feature descriptor for Quarkus
  - Declares extension name and capabilities

- [ ] T012 Build and verify compilation: `./mvnw clean install -DskipTests`
  - No compilation errors in runtime or deployment modules
  - Verify JAR size < 500KB (Principle II constraint)

**Checkpoint**: Foundation ready - user story implementation can begin

---

## Phase 3: User Story 1 - Inject and Use a Basic String Publisher (Priority: P1) 🎯 MVP

**Goal**: Implement core NatsPublisher injection and basic publishing functionality validated through integration tests

**Independent Test**: Can be fully tested by:
1. Creating a Quarkus app with the extension
2. Injecting `@Inject NatsPublisher publisher`
3. Calling `publisher.publish("hello")`
4. Verifying message appears on NATS broker subject `test`

### Tests for User Story 1 (REQUIRED - TDD approach)

**⚠️ CRITICAL**: Write these tests FIRST, ensure they FAIL before implementation

- [ ] T013 [P] [US1] Create base test class `BasicPublisherTest.java` in `integration-tests/src/main/java/io/quarkus/easynats/it/`
  - `@QuarkusTest` annotation (dev mode test)
  - Test method: `testPublisherCanBeInjected()`
    - Verify `@Inject NatsPublisher publisher` provides a non-null instance
    - Assert publisher is usable
  - Test method: `testPublisherPublishesMessage()`
    - Call `publisher.publish("hello")`
    - Verify no exception is thrown
  - Test method: `testMessageAppearsOnBroker()`
    - Publish message to NATS via publisher
    - Subscribe to subject `test` using NATS CLI: `nats sub test`
    - Verify message arrives (may be done manually or via NATS client API)
  - Abstract methods to allow reuse in integration test

- [ ] T014 [P] [US1] Create integration test `BasicPublisherIT.java` in `integration-tests/src/main/java/io/quarkus/easynats/it/`
  - `@QuarkusIntegrationTest` annotation
  - Extends `BasicPublisherTest` to reuse all test methods
  - Runs against real NATS broker via docker-compose
  - Validates same scenarios as dev-mode test but in full integration environment

### Implementation for User Story 1

- [ ] T015 [US1] Implement CDI producer/bean registration in `QuarkusEasyNatsProcessor.java` (if not already done in T010)
  - Ensure `NatsPublisher` is discoverable via `@Inject` in test applications
  - Verify Arc container injects singleton instance

- [ ] T016 [US1] Run dev-mode test to validate implementation
  - `./mvnw -pl integration-tests test -Dtest=BasicPublisherTest`
  - All three test methods MUST pass
  - Verify message is published to NATS subject `test`

- [ ] T017 [US1] Run integration test with docker-compose
  - Start docker-compose: `docker-compose -f integration-tests/docker-compose-devservices.yml up -d`
  - Run integration test: `./mvnw clean install -Pit -Dtest=BasicPublisherIT`
  - All three test methods MUST pass in full integration mode
  - Verify connection to real NATS broker works

- [ ] T018 [US1] Verify test coverage >= 80%
  - Check coverage report for `NatsPublisher` and `NatsConnectionManager`
  - All public methods must have test coverage
  - Use JaCoCo or Surefire coverage reports

**Checkpoint**: User Story 1 is fully functional and independently testable

### Acceptance Criteria Validation

**Acceptance Scenario 1**: Extension provides injected NatsPublisher
- ✅ Verified by T013 `testPublisherCanBeInjected()`

**Acceptance Scenario 2**: Publisher sends message to default subject
- ✅ Verified by T013 `testPublisherPublishesMessage()`

**Acceptance Scenario 3**: Message appears on NATS broker at subject `test`
- ✅ Verified by T013 `testMessageAppearsOnBroker()` and T014 integration test

**Acceptance Scenario 4**: Message content is exact string, no modifications
- ✅ Verified by T013/T014 integration tests (message comparison)

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Improvements affecting all user stories (MVP complete validation)

- [ ] T019 [P] Clean up Docker Compose after testing: `docker-compose -f integration-tests/docker-compose-devservices.yml down`

- [ ] T020 Run full build validation: `./mvnw clean install -Pit`
  - ✅ Compilation gate: no errors
  - ✅ Unit test gate: all tests pass (runtime + deployment)
  - ✅ Integration test gate: all integration tests pass
  - ✅ Code coverage gate: >= 80% coverage
  - ✅ Architecture gate: no unauthorized runtime dependencies

- [ ] T021 Verify native image compilation is possible (future gate, not required for MVP)
  - `./mvnw clean package -Pnative` (may skip if GraalVM not installed)
  - Document any native image constraints

- [ ] T022 Review quickstart.md against implementation
  - Verify all steps in quickstart.md work with actual code
  - Test step-by-step: create app, add dependency, inject publisher, publish message
  - Update quickstart if any steps fail

- [ ] T023 Document build commands in README (if applicable)
  - Build: `./mvnw clean install`
  - Test: `./mvnw clean test`
  - Integration tests: `./mvnw clean install -Pit`
  - Dev mode: `./mvnw quarkus:dev`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS user stories
- **User Stories (Phase 3)**: All depend on Foundational phase completion
- **Polish (Phase 4)**: Depends on all user stories being complete

### Task Dependencies Within Phases

**Phase 1**:
- T001-T002: Can run in parallel (project verification)
- T003-T004: Can run in parallel (environment setup)
- T005: Depends on T002 (Maven configured)
- T006: Depends on T005 (broker running)
- T007: Depends on T006 (stream created)

**Phase 2**:
- T008: No dependencies (implement core connection manager)
- T009: Depends on T008 (NatsPublisher uses NatsConnectionManager)
- T010-T011: Can run in parallel (deployment processors)
- T012: Depends on T008, T009, T010, T011 (build after all implementations)

**Phase 3 (User Story 1)**:
- T013: Can start after Phase 2 (write tests first)
- T014: Depends on T013 (extend base test class)
- T015: Depends on T010/T011 (verify bean registration)
- T016: Depends on T013, T015 (run dev-mode test)
- T017: Depends on T014, T005/T006 (run integration test with broker)
- T018: Depends on T016, T017 (check coverage after tests pass)

**Phase 4**:
- T019: Depends on T017 (clean up after integration tests)
- T020: Depends on T018 (run full build after all tests pass)
- T021: Depends on T020 (native image optional)
- T022: Depends on T020 (validate quickstart works)
- T023: Depends on T022 (document after validation)

---

## Parallel Execution Opportunities

### Setup Phase (Phase 1)
```
Parallel Group 1: T001, T002 (project verification)
Parallel Group 2: T003, T004 (environment setup)
Sequential: T005 → T006 → T007 (broker startup)
```

### Foundational Phase (Phase 2)
```
Parallel Group 1: T008 (NatsConnectionManager)
Sequential: T008 → T009 (NatsPublisher depends on T008)
Parallel Group 2: T010, T011 (deployment processors - independent)
Sequential: All above → T012 (build after all implementations)
```

### User Story 1 (Phase 3)
```
Parallel Group 1: T013, T014 (write tests first - can write in parallel)
Sequential: T013 → T014 (T014 extends T013)
Sequential: Phase 2 → T015 (verify bean registration after foundational phase)
Sequential: T013, T015 → T016 (run dev test after writing tests and verifying beans)
Sequential: T014, T006 → T017 (run integration test after test and broker setup)
Sequential: T016, T017 → T018 (check coverage after both tests pass)
```

### Polish Phase (Phase 4)
```
Sequential: T018 → T019 (clean up after tests)
Sequential: T019 → T020 (run full build after cleanup)
Sequential: T020 → T021 (optional native image after build)
Sequential: T020 → T022 (validate quickstart after build)
Sequential: T022 → T023 (document after validation)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only) ✅ SCOPE OF THIS FEATURE

**Recommended Approach**:

1. **Setup Phase (Phase 1)**: 2-3 hours
   - Verify project structure and dependencies
   - Start docker-compose NATS broker
   - Create JetStream stream

2. **Foundational Phase (Phase 2)**: 4-6 hours
   - Implement `NatsConnectionManager` singleton with lifecycle management
   - Implement `NatsPublisher` with injection support
   - Create build-time processors for CDI registration
   - Verify compilation and JAR size

3. **User Story 1 (Phase 3)**: 4-6 hours
   - Write tests FIRST (dev-mode and integration)
   - Implement CDI bean registration in processor
   - Run tests and verify all scenarios pass
   - Achieve >= 80% code coverage

4. **Polish (Phase 4)**: 2-3 hours
   - Run full build validation
   - Verify quickstart instructions work
   - Document build commands
   - Optional: native image compilation

**Total Estimated Time**: 12-18 hours for complete MVP

**Stop and Validate**: After Phase 3, User Story 1 is production-ready. Phase 4 is polish only.

### Success Indicators

- ✅ All 5 functional requirements (FR-001 through FR-005) implemented
- ✅ All 4 acceptance scenarios pass
- ✅ Integration test passes with real NATS broker
- ✅ Code coverage >= 80%
- ✅ Quickstart guide works end-to-end
- ✅ Builds with Quarkus 3.27.0, Java 21
- ✅ No unauthorized dependencies in runtime JAR
- ✅ JAR size < 500KB

### Deferred (for Future MVPs)

- Subscriber functionality (future feature)
- CloudEvents format support (Principle V, deferred)
- Message type generics/typed publishers (future enhancement)
- Subject configuration via annotations (future enhancement)
- Health checks / observability (Principle VII, deferred)
- Error recovery / reconnection logic (future enhancement)

---

## Notes

- [P] tasks = different files, no dependencies within same phase
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Tests MUST be written before implementation (TDD per Principle III)
- Commit after each major task (e.g., after T009, T012, T018, T020)
- Use `git status` to verify staged changes before committing
- Stop at any checkpoint to validate story independently
- Phase 4 tasks can be deferred if MVP is sufficient for deployment
