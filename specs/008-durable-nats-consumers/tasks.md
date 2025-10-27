# Implementation Tasks: Durable Consumers for @NatsSubscriber

**Feature**: `008-durable-nats-consumers` | **Branch**: `008-durable-nats-consumers` | **Date**: 2025-10-27

**Spec**: `/specs/008-durable-nats-consumers/spec.md`
**Plan**: `/specs/008-durable-nats-consumers/plan.md`

---

## Quick Reference: Key Files & Code Patterns

**Build-Time**:
- `@NatsSubscriber` annotation: `runtime/src/main/java/org/mjelle/quarkus/easynats/annotation/NatsSubscriber.java`
- `QuarkusEasyNatsProcessor`: `deployment/src/main/java/org/mjelle/quarkus/easynats/processor/QuarkusEasyNatsProcessor.java`
- `SubscriberMetadata`: `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/metadata/SubscriberMetadata.java`

**Runtime Startup**:
- `SubscriberInitializer.onStart()`: `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/startup/SubscriberInitializer.java` (line 63)
- `SubscriberInitializer.initializeSubscriber()`: line 86 (where durable consumer verification will be added)
- Key JNATS APIs:
  - `jsm.getConsumerInfo(stream, consumer)` - verify consumer exists (durable mode)
  - `jsm.addOrUpdateConsumer(stream, config)` - create ephemeral consumer (existing)
  - `js.getConsumerContext(stream, consumerName)` - get context for both modes
  - `consumerContext.consume(handler)` - start message delivery

---

## Overview

This document contains all implementation tasks needed to add durable consumer support to the `@NatsSubscriber` annotation. The feature enables developers to bind subscriber methods to pre-configured NATS JetStream durable consumers, allowing message processing to survive application restarts.

**User Story**: As a developer, I want to specify a stream and durable consumer name in my `@NatsSubscriber` annotation so that my message consumer can bind to a pre-configured durable consumer on the NATS server and survive restarts without losing messages. (**Priority**: P1)

**Independent Test Criteria**:
- A Quarkus application with a single `@NatsSubscriber` method annotated with `stream` and `consumer` properties
- Message is published, app stops, another message is published, app restarts
- Both messages are processed (demonstrating durability)
- Build fails if annotation specifies both `subject` and `stream+consumer`
- Application startup fails with clear error if durable consumer doesn't exist on NATS server

---

## Dependency Graph & Execution Order

```
Phase 1 (Setup)
  ├─ T001: Project structure validation
  └─ Blocks: All downstream tasks

Phase 2 (Foundational)
  ├─ T002: Update @NatsSubscriber annotation signature
  ├─ T003: Understand existing QuarkusEasyNatsProcessor patterns
  └─ Blocks: Build-time validation, startup verification

User Story 1 (P1): Use Pre-configured Durable Consumer
  ├─ T004-T007: Build-time validation (subject/stream/consumer mutual exclusivity)
  ├─ T008-T011: Startup consumer existence verification
  ├─ T012-T016: Integration tests (message survival, error handling)
  └─ Blocks: Documentation
```

**Parallel Opportunities**:
- T004-T007 (build-time validation) can run in parallel with T008-T011 (startup verification)
- T012-T016 (integration tests) can run in parallel with implementation

**MVP Scope**: Complete User Story 1 (T001-T016) = durable consumer binding with message durability

---

## Phase 1: Setup & Verification

### Project Structure & Prerequisites

- [ ] T001 [P] Verify multi-module project structure: `runtime/`, `deployment/`, `integration-tests/` modules exist and dependencies are correctly configured in pom.xml

---

## Phase 2: Foundational Components

### Annotation Enhancement & Build-Time Infrastructure

- [ ] T002 [P] Update `@NatsSubscriber` annotation signature in `runtime/src/main/java/org/mjelle/quarkus/easynats/annotation/NatsSubscriber.java` to add:
  - `stream()` property (String, default="") - NATS JetStream stream name
  - `consumer()` property (String, default="") - durable consumer name
  - Document: "Use for durable consumer mode (pre-configured on NATS server)"

- [ ] T003 [P] Review `SubscriberMetadata` record in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/metadata/SubscriberMetadata.java` and understand:
  - How it captures annotation properties at build time
  - How it's passed to `SubscriberInitializer` at runtime (line 66-73 in SubscriberInitializer.java)
  - Will need to extend this record with `stream()` and `consumer()` properties in T008

---

## Phase 3: User Story 1 - Use Pre-configured Durable Consumer (Priority: P1)

### 3.1 Build-Time Validation: Annotation Property Mutual Exclusivity

**Goal**: Validate at build time that `subject` and `stream+consumer` properties are mutually exclusive.

**Acceptance Criteria**:
- ✅ Compilation succeeds when `subject` is non-empty and `stream`/`consumer` are empty (ephemeral mode)
- ✅ Compilation succeeds when `stream` and `consumer` are non-empty and `subject` is empty (durable mode)
- ❌ Compilation fails with clear error if both `subject` and `stream+consumer` are non-empty
- ❌ Compilation fails if only `stream` is provided without `consumer`
- ❌ Compilation fails if only `consumer` is provided without `stream`
- ❌ Compilation fails if `subject`, `stream`, and `consumer` are all empty

**Test Scenarios** (compilation tests in `integration-tests`):
1. Valid ephemeral: `@NatsSubscriber(subject = "logs.>")`
2. Valid durable: `@NatsSubscriber(stream = "orders", consumer = "processor")`
3. Invalid: `@NatsSubscriber(subject = "logs", stream = "orders", consumer = "processor")`
4. Invalid: `@NatsSubscriber(stream = "orders")`  (missing consumer)
5. Invalid: `@NatsSubscriber(consumer = "processor")`  (missing stream)
6. Invalid: `@NatsSubscriber()`  (no properties)

**Implementation Tasks**:

- [ ] T004 [P] [US1] Implement build-time validation in `QuarkusEasyNatsProcessor.java`: Extract `subject`, `stream`, `consumer` properties from discovered `@NatsSubscriber` annotations

- [ ] T005 [P] [US1] Add mutual exclusivity validation logic in `QuarkusEasyNatsProcessor.java`:
  - If (`subject` is non-empty) AND (`stream` is non-empty OR `consumer` is non-empty) → throw `DefinitionException("Cannot specify both subject and stream/consumer properties")`
  - If (`stream` is non-empty XOR `consumer` is non-empty) → throw `DefinitionException("stream and consumer properties must both be provided or both be empty")`
  - If (all three are empty) → throw `DefinitionException("Either subject (ephemeral) or stream+consumer (durable) must be provided")`

- [ ] T006 [P] [US1] Write unit tests for build-time validation in `deployment/src/test/java/org/mjelle/quarkus/easynats/processor/NatsSubscriberValidationTest.java`:
  - Test valid ephemeral mode annotation
  - Test valid durable mode annotation
  - Test invalid: both subject and stream/consumer
  - Test invalid: only stream without consumer
  - Test invalid: only consumer without stream
  - Test invalid: all properties empty

- [ ] T007 [US1] Write compilation tests in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/BuildTimeValidationTest.java`:
  - Use CompilationTest or similar pattern to verify build fails with appropriate annotation combinations
  - Verify error messages are clear and actionable

### 3.2 Startup Verification: Consumer Existence Check

**Goal**: Verify at application startup that durable consumers exist on NATS server.

**Acceptance Criteria**:
- ✅ Application starts successfully if durable consumer exists on NATS server
- ✅ Ephemeral mode subscriber starts successfully (unchanged from 004)
- ❌ Application startup fails with clear error message if durable consumer doesn't exist
- ✅ Error message includes: stream name, consumer name, remediation hint
- ✅ Consumer verification occurs during bean initialization (before message processing starts)

**Test Scenarios** (integration tests):
1. Consumer exists: App starts, subscriber active, ready to receive messages
2. Consumer doesn't exist: App fails to start with error message
3. Ephemeral consumer: App starts, creates ephemeral consumer (unchanged)

**Implementation Tasks**:

- [ ] T008 [P] [US1] Extend `SubscriberMetadata` record in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/metadata/SubscriberMetadata.java` to include durable consumer properties:
  - Add properties: `stream()` (String, nullable/empty), `consumer()` (String, nullable/empty)
  - Add helper method: `isDurableConsumer()` → returns `true` if both `stream` and `consumer` are non-empty
  - Update constructor/builder to populate these fields from annotation metadata

- [ ] T009 [P] [US1] Modify `SubscriberInitializer.initializeSubscriber()` method in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/startup/SubscriberInitializer.java` to handle durable consumer verification:
  - Current code (line 104-109): Creates ephemeral consumer using `jsm.addOrUpdateConsumer(streamName, consumerConfig)`
  - New code: Add conditional branch BEFORE consumer creation:
    ```java
    JetStreamManagement jsm = connectionManager.getConnection().jetStreamManagement();

    ConsumerInfo consumerInfo;
    if (metadata.isDurableConsumer()) {
        // Durable mode: verify consumer exists (FR-006)
        try {
            consumerInfo = jsm.getConsumerInfo(metadata.stream(), metadata.consumer());
            LOGGER.infof("Verified durable consumer: stream=%s, consumer=%s",
                metadata.stream(), metadata.consumer());
        } catch (Exception e) {
            // FR-007: Fail fast with clear error
            throw new IllegalStateException(
                "Failed to verify durable consumer: Stream '" + metadata.stream() +
                "' does not contain consumer '" + metadata.consumer() +
                "'. Please ensure the consumer is pre-configured on the NATS server.", e);
        }
    } else {
        // Ephemeral mode: create ephemeral consumer (existing logic)
        String streamName = resolveStreamName(metadata.subject());
        ConsumerConfiguration consumerConfig =
            EphemeralConsumerFactory.createEphemeralConsumerConfig(metadata.subject());
        consumerInfo = jsm.addOrUpdateConsumer(streamName, consumerConfig);
    }

    // Both paths: get ConsumerContext and consume (unchanged)
    JetStream js = connectionManager.getJetStream();
    ConsumerContext consumerContext = js.getConsumerContext(
        metadata.isDurableConsumer() ? metadata.stream() : streamName,
        consumerInfo.getName());
    consumerContext.consume(handler::handle);
    ```
  - Logs: At INFO level for both successful durable verification and ephemeral creation

- [ ] T010 [P] [US1] Update `QuarkusEasyNatsProcessor` in `deployment/src/main/java/org/mjelle/quarkus/easynats/processor/QuarkusEasyNatsProcessor.java` to populate `SubscriberMetadata` with `stream` and `consumer` properties:
  - When creating `SubscriberMetadata` from discovered `@NatsSubscriber` annotations, extract and populate:
    - `stream()` from `annotation.stream()` (or empty string if not set)
    - `consumer()` from `annotation.consumer()` (or empty string if not set)
  - These values are passed through to runtime at initialization time

- [ ] T011 [US1] Write startup verification tests in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/DurableConsumerVerificationTest.java`:
  - Pre-create durable consumer on NATS server
  - Start app with matching consumer annotation
  - Verify app starts successfully
  - Test non-existent consumer: Start app without pre-creating consumer
  - Verify app startup fails with clear error message

### 3.3 Integration Tests: Message Durability & Error Handling

**Goal**: Validate that durable consumers preserve messages across application restarts and handle errors correctly.

**Acceptance Criteria**:
- ✅ Message published before app stop is delivered after app restart
- ✅ Multiple messages queued while app is down are all delivered on restart
- ✅ Messages from ephemeral consumer are NOT persisted across restart
- ✅ Message redelivery works when subscriber method throws exception (inherited from 004)
- ✅ Durable and ephemeral consumers can coexist in same application

**Test Scenarios**:

1. **Message Survival Across Restart**:
   - Pre-create durable consumer on NATS
   - Start app with subscriber annotated with consumer
   - Publish message M1
   - Verify subscriber receives M1
   - Stop app
   - Publish message M2 (while app is down)
   - Restart app
   - Verify subscriber receives M2 (durability!)

2. **Multiple Messages in Queue**:
   - Pre-create durable consumer, start app
   - Stop app
   - Publish messages M1, M2, M3
   - Restart app
   - Verify all three are delivered in order

3. **Mixed Ephemeral & Durable**:
   - Same app with both `@NatsSubscriber(subject="...")` and `@NatsSubscriber(stream="...", consumer="...")`
   - Verify ephemeral messages are lost on restart
   - Verify durable messages persist

4. **Message Redelivery on Error**:
   - Subscriber method throws exception
   - Message is nak'd (not ack'd)
   - NATS redelivers message
   - (Pattern inherited from 004)

**Implementation Tasks**:

- [ ] T012 [P] [US1] Create integration test class `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/DurableConsumerMessageSurvivalTest.java` with:
  - `@QuarkusTest` for JVM testing
  - Inject NATS JetStream connection (or use REST endpoint pattern from existing tests)
  - Setup: Pre-create stream and durable consumer on NATS (via `@QuarkusTestResource`)

- [ ] T013 [P] [US1] Implement test method `testMessageSurvivalAcrossRestart()` in above class:
  - Publish message while app running
  - Verify subscriber received it
  - Stop app (simulated or actual restart)
  - Publish another message
  - Restart app (or verify queue still has message)
  - Assert both messages delivered

- [ ] T014 [P] [US1] Implement test method `testMultipleMessagesInQueue()`:
  - Publish M1, M2, M3 to queue
  - Stop app (message remains unprocessed)
  - Restart app
  - Assert all three delivered in order

- [ ] T015 [P] [US1] Implement test method `testMixedEphemeralAndDurable()`:
  - Same app: one ephemeral subscriber, one durable subscriber
  - Publish to ephemeral subject
  - Stop app
  - Publish to both subjects
  - Restart app
  - Assert only durable message delivered (ephemeral lost)

- [ ] T016 [US1] Create corresponding `*IT` class for native testing:
  - `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/DurableConsumerMessageSurvivalIT.java`
  - Extends `DurableConsumerMessageSurvivalTest`
  - `@QuarkusIntegrationTest` annotation (native image testing)

---

## Phase 4: Documentation & Polish

### User-Facing Documentation

- [ ] T017 Update Quarkus extension documentation to include durable consumer examples:
  - File: `docs/` or `README.md` (location TBD based on project structure)
  - Content: Copy from `/specs/001-durable-nats-consumers/quickstart.md`
  - Examples: ephemeral vs. durable, pre-configuration, error handling

- [ ] T018 Update CLAUDE.md with contributor notes:
  - File: `/CLAUDE.md`
  - Add section: "Feature 001: Durable Consumers"
  - Link to: spec.md, plan.md, data-model.md, contracts.md
  - Include: Build-time validation patterns, startup verification pattern, test structure

- [ ] T019 Add NATS CLI command examples to documentation:
  - File: Docs or quickstart
  - Commands: stream add, consumer add, consumer ls, consumer info
  - Include: Example filter subjects, ack modes, flow control options

---

## Phase 5: Quality Assurance & Verification

### Build & Test Validation

- [ ] T020 [P] Run full build to verify no compilation errors:
  - Command: `./mvnw clean install -DskipTests`
  - Expected: Success (all three modules compile)

- [ ] T021 [P] Run all unit tests:
  - Command: `./mvnw clean test`
  - Expected: All tests pass (coverage ≥80% on new code)
  - Modules: runtime, deployment

- [ ] T022 Run integration tests (JVM):
  - Command: `./mvnw -pl integration-tests clean test`
  - Expected: All tests pass
  - Tests: DurableConsumerVerificationTest, DurableConsumerMessageSurvivalTest, BuildTimeValidationTest

- [ ] T023 Run integration tests (native, if GraalVM available):
  - Command: `./mvnw clean install -Pit`
  - Expected: All tests pass (includes *IT native tests)
  - Validates: Native image compilation compatibility

---

## Implementation Strategy

### MVP Scope (Complete User Story 1)

**Minimum Viable Product** includes:
- ✅ Annotation properties: `stream`, `consumer` (T002)
- ✅ Build-time validation: mutual exclusivity enforcement (T004-T007)
- ✅ Startup verification: consumer existence check (T008-T011)
- ✅ Integration tests: message durability validation (T012-T016)
- ✅ Documentation: quickstart guide (T017-T019)

**MVP does NOT include** (deferred to future features):
- Manual ack/nak control (`NatsMessage<T>` wrapper pattern)
- Consumer lag monitoring
- Advanced consumer configuration options

### Implementation Flow (Data Path)

1. **Build Time** (T002, T004-T007, T010):
   - Annotation `@NatsSubscriber(stream="X", consumer="Y")` is discovered
   - `QuarkusEasyNatsProcessor` validates properties (mutual exclusivity)
   - Annotation properties extracted and stored in `SubscriberMetadata` record
   - Metadata is encoded/serialized for runtime (build step output)

2. **Runtime Startup** (T008-T009, T011):
   - `SubscriberInitializer.onStart()` reads `SubscriberMetadata` from registry (line 66)
   - For each subscriber:
     - If `isDurableConsumer()`: call `jsm.getConsumerInfo(stream, consumer)` to verify existence
     - If ephemeral: call `jsm.addOrUpdateConsumer()` to create consumer
     - In both cases: get `ConsumerContext` and call `consumerContext.consume(handler)`
   - If verification fails: throw exception → application startup fails with clear error

3. **Message Processing** (inherited from 004, unchanged):
   - Messages delivered via `ConsumerContext.consume()`
   - Handler invokes user method
   - On success: message ack'd
   - On exception: message nak'd (redelivery by NATS)

### Incremental Delivery

1. **Iteration 1** (T001-T003): Setup, annotation enhancement, metadata review
2. **Iteration 2** (T004-T007): Build-time validation [P can run in parallel with Iteration 3]
3. **Iteration 3** (T008-T010): Metadata extension & SubscriberInitializer modification [P can run in parallel with Iteration 2]
4. **Iteration 4** (T011, T012-T016): Startup verification tests & integration tests
5. **Iteration 5** (T017-T019): Documentation
6. **Iteration 6** (T020-T023): QA & gates

### Code Coverage Target

- **Runtime module**: ≥80% coverage on new code:
  - `SubscriberMetadata`: new `stream()`, `consumer()`, `isDurableConsumer()` methods
  - `SubscriberInitializer.initializeSubscriber()`: durable consumer verification branch (lines 143-155 in modified version)
- **Deployment module**: ≥80% coverage on new code:
  - `QuarkusEasyNatsProcessor`: annotation property extraction and population of SubscriberMetadata
- **Integration tests**: All acceptance scenarios must pass (T011-T016)

---

## Task Summary

| Phase | Count | Tasks |
|-------|-------|-------|
| Setup | 1 | T001 |
| Foundational | 2 | T002-T003 |
| Build-Time Validation | 4 | T004-T007 |
| Startup Verification | 4 | T008-T011 |
| Integration Tests | 5 | T012-T016 |
| Documentation | 3 | T017-T019 |
| QA & Verification | 4 | T020-T023 |
| **TOTAL** | **23** | **T001-T023** |

**Parallelizable Tasks**: T001-T003 (setup/foundational are blocking), [T004-T007 in parallel with T008-T011], [T012-T016 in parallel with documentation]

---

## Success Criteria (Definition of Done)

✅ All tasks marked complete
✅ Build succeeds: `./mvnw clean install -DskipTests`
✅ Unit tests pass: `./mvnw clean test` (≥80% coverage)
✅ Integration tests pass: `./mvnw -pl integration-tests clean test`
✅ Native tests pass: `./mvnw clean install -Pit` (if GraalVM available)
✅ Durable consumer message survival demonstrated in integration test
✅ Build-time validation prevents invalid annotations
✅ Startup verification fails with clear error for missing consumer
✅ Documentation updated and accessible
✅ All commits pass constitution checks (7 principles)

---

## Reference Documents

- **Spec**: `/specs/008-durable-nats-consumers/spec.md`
- **Plan**: `/specs/008-durable-nats-consumers/plan.md`
- **Data Model**: `/specs/008-durable-nats-consumers/data-model.md`
- **Contracts**: `/specs/008-durable-nats-consumers/contracts.md`
- **Quickstart**: `/specs/008-durable-nats-consumers/quickstart.md`
- **Constitution**: `/.specify/memory/constitution.md`
- **CLAUDE.md**: `/CLAUDE.md` (contributor guidelines)
