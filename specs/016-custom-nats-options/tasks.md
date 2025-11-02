# Implementation Tasks: Custom NATS Options via CDI

**Feature**: 016-custom-nats-options
**Branch**: `016-custom-nats-options`
**Created**: 2025-11-02
**Status**: Ready for Implementation

---

## Overview

This document contains the actionable implementation tasks for enabling advanced developers to provide custom `io.nats.client.Options` via CDI to the Quarkus Easy NATS extension.

### Feature Scope

- **User Story 1 (P1)**: Advanced developer provides custom NATS Options via CDI bean
- **User Story 2 (P2)**: Custom Options integrate seamlessly with existing extension features

### Key Implementation Details

- **New Files**: `NatsConnectionProducer.java`, `NatsConnectionProducerTest.java`
- **Modified Files**: `NatsConnection.java` (inject Options via constructor)
- **Testing**: Unit tests only (no integration tests needed; CDI handles validation)
- **Architecture**: `@DefaultBean` CDI producer + developer override via unqualified bean

---

## Task Organization Strategy

Tasks are organized by **user story** to enable independent implementation and testing. Each user story is a complete, independently deployable increment.

### Execution Order

1. **Phase 1**: Setup (foundational/shared)
2. **Phase 2**: User Story 1 (P1) - Core functionality
3. **Phase 3**: User Story 2 (P2) - Integration with existing features
4. **Phase 4**: Polish & Quality Assurance

### Parallelization

- Tasks marked `[P]` are parallelizable (independent, can run concurrently)
- Within a story phase, multiple `[P]` tasks can be implemented in parallel
- Non-parallelizable tasks have dependencies on previous tasks

---

## Phase 1: Setup & Foundational Tasks

### Goal

Prepare the runtime and test infrastructure for the Options injection feature.

### Tasks

- [ ] T001 Review existing NatsConfiguration and NatsConnection code in runtime module
- [ ] T002 Understand CDI @DefaultBean pattern and how it works in Quarkus Arc
- [ ] T003 Create NatsConnectionProducer class skeleton in `runtime/src/main/java/org/mjelle/quarkus/easynats/core/NatsConnectionProducer.java`
- [ ] T004 Create NatsConnectionProducerTest class skeleton in `runtime/src/test/java/org/mjelle/quarkus/easynats/core/NatsConnectionProducerTest.java`

---

## Phase 2: User Story 1 (P1) - Advanced Developer Provides Custom NATS Options

### Story Goal

Enable advanced developers to provide custom `io.nats.client.Options` beans via CDI, completely overriding the default options created from `NatsConfiguration` properties.

### Independent Test Criteria

✅ An application can provide a custom unqualified `Options` bean
✅ NatsConnection receives and uses the custom Options for connection setup
✅ NatsConfiguration properties are completely bypassed when custom bean exists
✅ Unit tests verify default Options producer works correctly
✅ Application compiles and starts with both default and custom Options scenarios

### Implementation Tasks

#### Task Group 1: Implement Default Options Producer

- [ ] T005 [P] [US1] Implement `@DefaultBean` producer method in NatsConnectionProducer that reads NatsConfiguration properties (servers, username, password, ssl-enabled)
- [ ] T006 [P] [US1] Add validation logic in producer to ensure servers, username, and password are not empty when creating default Options
- [ ] T007 [P] [US1] Handle SSL configuration in default producer (ssl-enabled property → .secure() on Options.Builder)
- [ ] T008 [P] [US1] Add javadoc to producer method documenting @DefaultBean behavior and override capability

#### Task Group 2: Update NatsConnection for Options Injection

- [ ] T009 [US1] Modify NatsConnection constructor to accept injected `io.nats.client.Options` parameter
- [ ] T010 [US1] Remove any direct Options creation from NatsConnection (should only use injected instance)
- [ ] T011 [US1] Update NatsConnection to pass injected Options to NATS connection establishment code

#### Task Group 3: Unit Tests for Producer

- [ ] T012 [P] [US1] Write unit test for default producer: verifies Options are created from valid NatsConfiguration properties in `runtime/src/test/java/org/mjelle/quarkus/easynats/core/NatsConnectionProducerTest.java`
- [ ] T013 [P] [US1] Write unit test for producer validation: servers property is required and non-empty
- [ ] T014 [P] [US1] Write unit test for producer validation: username and password must both be present or both absent
- [ ] T015 [P] [US1] Write unit test for SSL configuration: ssl-enabled property correctly sets secure() on Options
- [ ] T016 [P] [US1] Write unit test for producer with missing servers: throws clear exception with helpful message
- [ ] T017 [US1] Run tests and verify ≥80% code coverage for NatsConnectionProducer class

#### Task Group 4: Integration Verification

- [ ] T018 [US1] Build and run `./mvnw clean install -DskipTests` to verify compilation gate passes
- [ ] T019 [US1] Run `./mvnw clean test` for runtime module to verify unit tests pass
- [ ] T020 [US1] Verify NatsConnection can be injected in a test application and successfully connects to NATS

### Story Completion Criteria

✅ NatsConnectionProducer implements @DefaultBean correctly
✅ Default producer reads NatsConfiguration properties accurately
✅ NatsConnection constructor accepts and uses injected Options
✅ All unit tests pass with ≥80% coverage
✅ Compilation gate passes (mvnw clean install -DskipTests)
✅ Application can start and connect with default Options from properties

---

## Phase 3: User Story 2 (P2) - Custom Options Integration

### Story Goal

Ensure that developers can provide custom `Options` beans that completely override the default, and that custom Options work seamlessly with existing extension features (CloudEvents, typed subscribers, durable consumers).

### Independent Test Criteria

✅ A custom unqualified Options bean overrides the default producer (CDI behavior)
✅ NatsConfiguration properties are ignored when custom bean exists
✅ Custom Options bean can use @Unremovable to prevent optimization
✅ CloudEvents functionality works with custom Options (existing feature integration)
✅ Typed subscribers work with custom Options (existing feature integration)
✅ Durable consumers work with custom Options (existing feature integration)

### Implementation Tasks

#### Task Group 1: Documentation & Examples

- [ ] T021 [P] [US2] Update `quickstart.md` with comprehensive Pattern 2 (custom Options) documentation including caveats (already completed - verify it covers all requirements)
- [ ] T022 [P] [US2] Update `contracts/options-producer-contract.md` with "Important Caveats for Custom Beans" section (already completed - verify comprehensiveness)
- [ ] T023 [US2] Create example custom Options bean class: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/config/CustomOptionsProvider.java`
- [ ] T024 [US2] Document in example class: CDI @Produces @Unremovable annotations and their purpose

#### Task Group 2: Custom Bean Documentation

- [ ] T025 [P] [US2] Document @Unremovable annotation requirement in code comments (WHY it's needed, WHAT happens if missing)
- [ ] T026 [P] [US2] Document that NatsConfiguration is completely bypassed when custom bean exists
- [ ] T027 [P] [US2] Document that multiple unqualified beans cause CDI AmbiguousResolutionException
- [ ] T028 [US2] Create troubleshooting guide: "Common mistakes with custom Options beans" in documentation

#### Task Group 3: Existing Feature Compatibility

- [ ] T029 [US2] Review CloudEvents implementation to ensure it works with any Options configuration (no special handling needed)
- [ ] T030 [US2] Review typed subscriber implementation to ensure it works with any Options configuration
- [ ] T031 [US2] Review durable consumer implementation to ensure it works with any Options configuration
- [ ] T032 [US2] Verify that custom Options don't break existing health checks or observability (tracing, metrics)

#### Task Group 4: Integration & Final Testing

- [ ] T033 [US2] Run `./mvnw clean install` to verify compilation with all modules
- [ ] T034 [US2] Verify unit tests still pass: `./mvnw clean test`
- [ ] T035 [US2] Verify code coverage remains ≥80% for new code
- [ ] T036 [US2] Verify native image gate: `./mvnw clean install -Pit` (native image compilation succeeds)

### Story Completion Criteria

✅ Custom Options documentation is comprehensive and emphasizes caveats
✅ Example custom Options bean provided
✅ @Unremovable annotation is documented and required
✅ Existing features (CloudEvents, subscribers, consumers) work with custom Options
✅ All tests pass in both JVM and native image environments
✅ Code coverage ≥80% maintained

---

## Phase 4: Polish & Quality Assurance

### Goal

Ensure code quality, documentation completeness, and Constitution compliance.

### Tasks

- [ ] T037 [P] Verify Principle I (Extension-First): Producer in runtime, no modifications to deployment module structure
- [ ] T038 [P] Verify Principle II (Minimal Runtime Dependencies): No new dependencies added to runtime module
- [ ] T039 [P] Verify Principle III (TDD): All public methods have unit tests, ≥80% coverage
- [ ] T040 [P] Verify Principle IV (Java 21): All code uses Java 21 features where appropriate
- [ ] T041 [P] Verify Principle V (CloudEvents): Custom Options don't break CloudEvents compliance
- [ ] T042 [P] Verify Principle VI (Developer Experience): CDI injection pattern is intuitive and well-documented
- [ ] T043 [P] Verify Principle VII (Observability): Custom Options don't break health checks or tracing
- [ ] T044 Run `./mvnw clean install -DskipTests` and confirm final compilation gate passes
- [ ] T045 Run `./mvnw clean test` for all modules and confirm all tests pass
- [ ] T046 Verify JAR size: Runtime module JAR ≤ 500 KB
- [ ] T047 Update CLAUDE.md with any new patterns or examples from this feature
- [ ] T048 Create summary commit message with all changes documented
- [ ] T049 Final code review: Ensure no technical debt or incomplete implementations

---

## Task Dependency Graph

```
Phase 1: Setup
├── T001-T004 (Independent setup tasks)
│
Phase 2: User Story 1 (Core Functionality)
├── T005-T008 [P] (Default producer implementation - can run in parallel)
├── T009-T011 (NatsConnection updates - depends on T005-T008 conceptual understanding)
├── T012-T016 [P] (Unit tests - can run in parallel, depends on implementation complete)
├── T017 (Coverage check)
├── T018-T020 (Integration verification)
│
Phase 3: User Story 2 (Integration)
├── T021-T022 [P] (Documentation - can run in parallel)
├── T023-T024 (Example custom bean)
├── T025-T028 [P] (Custom bean documentation - can run in parallel)
├── T029-T032 (Compatibility verification)
├── T033-T036 (Integration testing)
│
Phase 4: Polish
├── T037-T043 [P] (Quality gates - can run in parallel)
├── T044-T046 (Final verification)
├── T047-T049 (Documentation and commit)
```

---

## Parallelization Opportunities

### Phase 2 Parallel Execution (User Story 1)

These tasks can be executed in parallel:

```
Parallel Group 1 (Implementation):
  T005: Default producer property reading
  T006: Producer validation
  T007: SSL configuration
  T008: Javadoc

Parallel Group 2 (Tests):
  T012: Valid properties test
  T013: Servers validation test
  T014: Username/password validation test
  T015: SSL configuration test
  T016: Missing servers error test
  → T017: (Coverage check - depends on tests complete)
```

### Phase 3 Parallel Execution (User Story 2)

```
Parallel Group 1 (Documentation):
  T021: quickstart.md verification
  T022: contract.md verification
  T025: Code comments
  T026: NatsConfiguration bypass documentation
  T027: Multiple beans documentation
  T028: Troubleshooting guide

Parallel Group 2 (Verification):
  T029: CloudEvents compatibility
  T030: Typed subscribers compatibility
  T031: Durable consumers compatibility
  T032: Health/observability verification
  → T033-T036: (Testing - depends on above complete)
```

### Phase 4 Parallel Execution (Polish)

All quality gate tasks (T037-T043) can run in parallel:
- T037: Extension-first verification
- T038: Dependencies verification
- T039: Test coverage verification
- T040: Java 21 verification
- T041: CloudEvents verification
- T042: Developer experience verification
- T043: Observability verification

---

## MVP (Minimum Viable Product) Scope

**MVP includes**: User Story 1 (Phase 2) only

### MVP Feature Set

✅ Default `@DefaultBean` CDI producer that reads `NatsConfiguration` properties
✅ Developer can override with unqualified `Options` bean
✅ NatsConnection accepts and uses injected Options
✅ Unit tests verify producer works correctly
✅ All compilation and unit test gates pass

### MVP Exclusions (for Phase 2)

❌ User Story 2 integration (nice-to-have for completeness but not blocking)
❌ Extended documentation beyond inline comments
❌ Native image testing (Phase 4)

### MVP Timeline

- Phase 1 Setup: 1-2 hours
- Phase 2 Implementation (T005-T020): 4-6 hours
- **Total MVP**: 5-8 hours

---

## Testing Strategy

### Unit Testing (Required)

**Location**: `runtime/src/test/java/org/mjelle/quarkus/easynats/core/NatsConnectionProducerTest.java`

**Test Coverage** (Target: ≥80%):
- ✅ Default producer with valid configuration
- ✅ Server URL validation (empty/null)
- ✅ Username/password pairing validation
- ✅ SSL configuration handling
- ✅ Error messages are clear and actionable

### Integration Testing (NOT REQUIRED)

- ❌ Custom Options override: Validated by CDI itself (extension has no role)
- ❌ Compatibility with CloudEvents: Existing extension features already tested
- ❌ Compatibility with subscribers: Existing extension features already tested

**Rationale**: When developers provide custom Options beans, CDI handles bean resolution and injection. The extension has no custom logic for this scenario - it simply receives the injected bean. Extension testing is limited to default producer validation.

---

## Definition of Done (Per Task)

Each task is complete when:

1. ✅ Code written and compiles without errors
2. ✅ Tests pass (if applicable)
3. ✅ Code review completed
4. ✅ Documentation updated (if applicable)
5. ✅ No new compiler warnings introduced
6. ✅ Follows Java 21 + Quarkus best practices
7. ✅ No violations of project Constitution principles

---

## Success Criteria (Story Complete)

### User Story 1 Complete When

✅ `NatsConnectionProducer` class exists with @DefaultBean producer
✅ Producer reads NatsConfiguration properties correctly
✅ Producer validates required properties (servers, username, password)
✅ Producer handles SSL configuration
✅ `NatsConnection` accepts Options via constructor injection
✅ All unit tests pass with ≥80% coverage
✅ Compilation gate passes

### User Story 2 Complete When

✅ Custom Options documentation is comprehensive
✅ Example custom bean is provided
✅ Caveats are clearly documented
✅ Compatibility with existing features verified
✅ Native image compilation succeeds

---

## Risk Mitigation

| Risk | Mitigation | Task |
|------|-----------|------|
| CDI bean optimization removes custom bean | Document @Unremovable requirement; provide example | T023-T024 |
| Multiple Options beans cause confusion | Document single bean rule; provide error message guide | T027-T028 |
| Developers try to use NatsConfiguration with custom bean | Document complete bypass; provide wrong/right examples | T025-T026 |
| Custom bean validation gaps | Unit tests cover edge cases (missing servers, mismatched auth) | T012-T016 |
| Native image reflection issues | Test with native image compilation; document any issues | T036 |

---

## Post-Implementation Checklist

- [ ] All tasks in this document marked complete
- [ ] Code review approval received
- [ ] All tests passing (JVM + native)
- [ ] Code coverage ≥80% for new code
- [ ] Documentation complete and reviewed
- [ ] No new compiler warnings
- [ ] Constitution compliance verified
- [ ] PR created and merged to master
- [ ] Feature branch deleted

---

**Last Updated**: 2025-11-02
**Status**: Ready for Implementation
**Total Tasks**: 49
**Estimated Effort**: 8-12 hours (MVP + Phase 2 integration)
