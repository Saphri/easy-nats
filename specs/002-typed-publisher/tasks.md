# Implementation Tasks: Typed NatsPublisher with CloudEvents Support

**Feature**: MVP 002 Typed NatsPublisher with CloudEvents Support
**Branch**: `002-typed-publisher`
**Date**: 2025-10-26
**Status**: Ready for Implementation (Phase 2)

---

## Overview

This document outlines all implementation tasks for MVP 002. Tasks are organized by phase:

1. **Phase 1 (Setup)**: Project initialization and infrastructure
2. **Phase 2 (Foundational)**: Shared utility classes and encoders
3. **Phase 3 (US1)**: Core typed publishing feature (JSON serialization)
4. **Phase 4 (US2)**: CloudEvents support with auto-generated headers
5. **Phase 5 (Polish)**: Integration testing, documentation, and edge cases

**Total Task Count**: ~25 tasks
**MVP Scope**: Complete Phase 1-3 (US1 implementation) for minimum viable typed publishing

---

## Task Dependencies & Execution Order

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational - TypedPayloadEncoder)
    ↓
Phase 3 (US1 - Core Typed Publishing) → CAN RUN IN PARALLEL: [P] Tasks
    ├─ T005: Create TypedPayloadEncoder (BLOCKS US1)
    ├─ T009: [P] Create NatsPublisher<T> extension
    └─ T013: [P] Create TypedPublisherResource endpoint
    ↓
Phase 4 (US2 - CloudEvents Support) → DEPENDS ON PHASE 3
    ├─ T015: Create CloudEventsHeaders factory
    ├─ T018: [P] Create CloudEventsPayload<T> wrapper
    ├─ T021: [P] Implement publishCloudEvent() method
    └─ T024: [P] Extend TypedPublisherResource for CloudEvents
    ↓
Phase 5 (Polish)
    ├─ T025: Error handling & edge cases
    ├─ T026: Manual integration testing
    └─ T027: Documentation finalization

**Parallel Execution Examples**:
- Phase 3: T009 and T013 can run in parallel (both use T005 TypedPayloadEncoder as dependency)
- Phase 4: T018, T021, T024 can run in parallel (all use T015 CloudEventsHeaders)
```

---

## Phase 1: Setup & Infrastructure

### Project Structure & Dependencies

- [x] T001 Verify Maven modules structure: runtime, deployment, integration-tests in `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/`

- [x] T002 Add Jackson dependency (BOM-managed) to runtime/pom.xml:
  - Dependency: `quarkus-jackson` (from Quarkus BOM)
  - No version override needed (inherited from parent BOM Quarkus 3.27.0)
  - Verify no transitive bloat: `./mvnw dependency:tree -pl runtime | grep jackson`

- [x] T003 Verify quarkus-jackson is available in runtime/pom.xml by running: `./mvnw clean install -DskipTests`

- [x] T004 Create integration-tests test classes directory structure if not exists:
  - `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/`
  - `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/`

---

## Phase 2: Foundational - Shared Encoder/Decoder Infrastructure

### TypedPayloadEncoder Utility Class

**Purpose**: Central encoder/decoder resolution with priority chain: primitives → bytes (base64) → arrays → Jackson

- [x] T005 Create TypedPayloadEncoder utility class in `runtime/src/main/java/org/mjelle/quarkus/easynats/TypedPayloadEncoder.java`
  - Method: `canEncodeNatively(Class<?> type): boolean`
    - Returns true if: primitive wrappers, String, byte/Byte/byte[], or primitive arrays
    - Returns false for: POJOs, custom classes, collections
  - Method: `encodeNatively(Object value): byte[]`
    - Primitives: `toString().getBytes(UTF_8)`
    - String: Direct UTF-8 encoding
    - Byte types: `Base64.getEncoder().encodeToString(bytes).getBytes(UTF_8)` (NEVER raw binary)
    - Primitive arrays: Space-separated (int[]) or comma-separated (String[]): `toString().getBytes(UTF_8)`
  - Method: `encodeWithJackson(Object value, ObjectMapper mapper): byte[]`
    - Call `mapper.writeValueAsBytes(value)`
    - Catch `JsonProcessingException` → throw `SerializationException` with message: "Failed to serialize {ClassName}: {cause}"
  - Method: `resolveEncoder(Class<?> type): PayloadEncoderStrategy` enum (NATIVE_ENCODER or JACKSON_ENCODER)

- [x] T006 Create SerializationException checked exception in `runtime/src/main/java/org/mjelle/quarkus/easynats/SerializationException.java`
  - Extends `Exception`
  - Constructor: `SerializationException(String message)` and `SerializationException(String message, Throwable cause)`
  - Used for Jackson failures and non-serializable objects

- [x] T007 [P] Write unit tests for TypedPayloadEncoder in `runtime/src/test/java/org/mjelle/quarkus/easynats/TypedPayloadEncoderTest.java`
  - Test: `canEncodeNatively()` returns true for int, long, String, byte[], etc.
  - Test: `canEncodeNatively()` returns false for custom POJOs
  - Test: `encodeNatively(42)` → "42".getBytes(UTF_8)
  - Test: `encodeNatively("hello")` → "hello".getBytes(UTF_8)
  - Test: `encodeNatively(new byte[]{1,2,3})` → base64-encoded string (NOT raw binary)
  - Test: `encodeNatively(new int[]{1,2,3})` → "1 2 3".getBytes(UTF_8)
  - Test: `encodeWithJackson()` serializes POJO to JSON
  - Test: `encodeWithJackson()` throws SerializationException on failure (e.g., no zero-arg constructor)
  - Assert using AssertJ (NOT JUnit assertions per CLAUDE.md)

- [x] T008 Verify unit tests pass: `./mvnw clean test -pl runtime`

---

## Phase 3: User Story 1 - Core Typed Publishing (JSON Serialization)

### NatsPublisher<T> Extension with Generic Types

**Goal**: Enable type-safe publishing of domain objects as JSON without manual serialization

**Independent Test Criteria**:
1. Developer can inject `NatsPublisher<Order>` where Order is a custom POJO
2. Calling `publisher.publish(new Order(...))` sends JSON to NATS subject "test"
3. JSON is human-readable and contains all order fields
4. Multiple messages of same type can be consumed and deserialized independently

- [x] T009 [P] [US1] Extend NatsPublisher<T> class in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisher.java`
  - **Current state**: MVP 001 has untyped `publish(String message)` and hardcoded subject "test"
  - **Change**: Add generic type parameter `<T>`
  - Method: `publish(T payload): void`
    - Null check: `if (payload == null) throw new IllegalArgumentException("Cannot publish null object")`
    - Get runtime class: `payload.getClass()`
    - Resolve encoder: `TypedPayloadEncoder.resolveEncoder(payload.getClass())`
    - If native: Call `TypedPayloadEncoder.encodeNatively(payload)`
    - If Jackson: Inject `ObjectMapper` from Quarkus and call `TypedPayloadEncoder.encodeWithJackson(payload, mapper)`
    - Publish to NATS subject "test" using `connectionManager`
  - Constructor: Remains `NatsPublisher(NatsConnectionManager connectionManager)` (constructor injection)
  - Note: Subject is hardcoded "test" per MVP 002 spec (no @NatsSubject annotation)

- [x] T010 [P] [US1] Inject ObjectMapper in NatsPublisher
  - **Option A** (preferred): Constructor-inject `ObjectMapper` via Arc container
    - ObjectMapper is provided by `quarkus-jackson` extension
    - Update constructor: `NatsPublisher(NatsConnectionManager connectionManager, ObjectMapper mapper)`
  - **Option B**: Access via Arc context (less preferred, but acceptable if A fails)
  - Verify constructor injection works: Check CLAUDE.md dependency injection rules

- [x] T011 [P] [US1] Write unit tests for NatsPublisher<T>.publish() in `runtime/src/test/java/org/mjelle/quarkus/easynats/NatsPublisherTest.java`
  - Test: `publish(String "hello")` encodes natively (no Jackson)
  - Test: `publish(Integer 42)` encodes natively
  - Test: `publish(null)` throws IllegalArgumentException with message "Cannot publish null object"
  - Test: `publish(customPOJO)` uses Jackson encoder and sends JSON
  - Test: SerializationException is thrown for non-serializable objects
  - Note: Use mock `NatsConnectionManager` for unit testing (don't test actual NATS connection)

- [x] T012 [US1] Verify unit tests pass: `./mvnw clean test -pl runtime`

- [x] T013 [P] [US1] Create TypedPublisherResource REST endpoint in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/TypedPublisherResource.java`
  - Annotation: `@Path("/typed-publisher")`
  - Dependency: Inject `NatsConnectionManager` (create instance of `NatsPublisher<?>` for testing)
  - Endpoint: `POST /typed-publisher/publish`
    - Request body: `PublishRequest` record with fields: `objectType: String`, `payload: Object`
    - Use reflection to instantiate generic `NatsPublisher<T>` for requested type
    - Call `publisher.publish(payload)`
    - Response: `PublishResult` record with fields: `status: String` ("published"), `objectType: String`, `subject: String` ("test"), `message: String`
    - Error handling: Return 400 for IllegalArgumentException, 500 for SerializationException
  - Return type: Use POJOs (NOT jakarta.ws.rs.core.Response per CLAUDE.md)

- [x] T014 [US1] Write integration test for typed publishing in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/TypedPublisherTest.java`
  - Setup: `@QuarkusTest` annotation; start NATS broker via docker-compose
  - Test: POST to `/typed-publisher/publish` with String payload → verify 200 response
  - Test: POST to `/typed-publisher/publish` with custom POJO → verify 200 response
  - Test: POST to `/typed-publisher/publish` with null payload → verify 400 response
  - Note: Use RestAssured for HTTP testing (per CLAUDE.md example from MVP 001)
  - Note: No NATS message verification in unit test (save for manual testing in Phase 5)

---

## Phase 4: User Story 2 - CloudEvents Support

### CloudEventsHeaders Factory & CloudEventsPayload Wrapper

**Goal**: Add CloudEvents spec 1.0 compliance with auto-generated metadata headers

**Independent Test Criteria**:
1. Developer calls `publisher.publishCloudEvent(event, type, source)`
2. Message arrives on NATS with ce-* headers in message headers
3. ce-id and ce-time are auto-generated (UUIDs and ISO 8601)
4. ce-type and ce-source are provided OR auto-generated from class name and hostname

- [x] T015 Create CloudEventsHeaders factory class in `runtime/src/main/java/org/mjelle/quarkus/easynats/CloudEventsHeaders.java`
  - Constants:
    - `SPEC_VERSION = "1.0"`
    - `PREFIX = "ce-"`
    - `HEADER_SPECVERSION = "ce-specversion"`
    - `HEADER_TYPE = "ce-type"`
    - `HEADER_SOURCE = "ce-source"`
    - `HEADER_ID = "ce-id"`
    - `HEADER_TIME = "ce-time"`
    - `HEADER_DATACONTENTTYPE = "ce-datacontenttype"`
  - Method: `generateId(): String`
    - Return `UUID.randomUUID().toString()`
  - Method: `generateTime(): String`
    - Return `Instant.now().toString()` (ISO 8601 UTC)
  - Method: `generateType(Class<?> payloadClass): String`
    - Return `payloadClass.getCanonicalName()` (fully-qualified class name)
  - Method: `generateSource(): String`
    - Try: `InetAddress.getLocalHost().getHostName()`
    - Fallback: System property "app.name"
    - Fallback: "localhost"
  - Method: `createHeaders(Class<?> payloadClass, String ceTypeOverride, String ceSourceOverride): io.nats.client.api.Headers`
    - Create NATS Headers object
    - Set ce-specversion: "1.0"
    - Set ce-type: ceTypeOverride OR auto-generated
    - Set ce-source: ceSourceOverride OR auto-generated
    - Set ce-id: Always auto-generated
    - Set ce-time: Always auto-generated
    - Set ce-datacontenttype: "application/json"
    - Return populated Headers

- [x] T016 [P] Write unit tests for CloudEventsHeaders in `runtime/src/test/java/org/mjelle/quarkus/easynats/CloudEventsHeadersTest.java`
  - Test: `generateId()` returns valid UUID format
  - Test: `generateTime()` returns valid ISO 8601 string
  - Test: `generateType(Order.class)` returns fully-qualified class name
  - Test: `generateSource()` returns non-empty hostname/identifier
  - Test: `createHeaders()` with explicit ceType → ce-type header matches input
  - Test: `createHeaders()` with null ceType → ce-type auto-generated from class name
  - Test: `createHeaders()` → all 6 ce-* headers present and correct
  - Use AssertJ assertions

- [x] T017 [US2] Verify unit tests pass: `./mvnw clean test -pl runtime`

- [x] T018 [P] [US2] Create CloudEventsPayload<T> immutable wrapper in `runtime/src/main/java/org/mjelle/quarkus/easynats/CloudEventsPayload.java`
  - Record or class with final fields:
    - `data: T` (non-null)
    - `ceType: String` (nullable)
    - `ceSource: String` (nullable)
    - `ceId: String` (non-null, auto-generated)
    - `ceTime: String` (non-null, auto-generated)
  - Constructor: `CloudEventsPayload(T data, String ceType, String ceSource, String ceId, String ceTime)`
    - Validate: `data` cannot be null
    - Validate: `ceId` cannot be null
    - Validate: `ceTime` cannot be null
  - Accessor methods: `getData()`, `getCeType()`, `getCeSource()`, `getCeId()`, `getCeTime()`
  - No setters (immutable)

- [x] T019 [P] [US2] Extend NatsPublisher<T> with publishCloudEvent() method in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisher.java`
  - Method signature: `publishCloudEvent(T payload, String ceType, String ceSource): void`
  - Null check: `if (payload == null) throw new IllegalArgumentException("Cannot publish null object")`
  - Call `TypedPayloadEncoder.resolveEncoder()` (same as publish())
  - Encode payload (native or Jackson)
  - Generate headers: `CloudEventsHeaders.createHeaders(payload.getClass(), ceType, ceSource)`
  - Publish to NATS subject "test" WITH headers using `connectionManager.getConnection().publish(subject, headers, encodedPayload)`
  - Error handling: SerializationException for encoding failures

- [x] T020 [P] [US2] Write unit tests for NatsPublisher<T>.publishCloudEvent() in `runtime/src/test/java/org/mjelle/quarkus/easynats/NatsPublisherTest.java`
  - Add to existing NatsPublisherTest class
  - Test: `publishCloudEvent(event, "com.example.Order", "/order-service")` → headers set correctly
  - Test: `publishCloudEvent(event, null, null)` → headers auto-generated
  - Test: `publishCloudEvent(null, ...)` → throws IllegalArgumentException
  - Test: SerializationException for non-serializable objects
  - Mock `NatsConnectionManager` to verify headers are passed to publish()

- [x] T021 [P] [US2] Extend TypedPublisherResource with CloudEvents endpoint in `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/TypedPublisherResource.java`
  - Endpoint: `POST /typed-publisher/publish-cloudevents`
    - Request body: `PublishCloudEventsRequest` record with: `objectType: String`, `payload: Object`, `ceType: String?`, `ceSource: String?`
    - Call `publisher.publishCloudEvent(payload, ceType, ceSource)`
    - Response: `PublishCloudEventsResult` record with: `status: String` ("published"), `objectType: String`, `subject: String`, `ceType: String`, `ceSource: String`, `ceId: String`, `ceTime: String`, `message: String`
    - Return generated ce-* values in response for verification

- [x] T022 [P] [US2] Write integration test for CloudEvents in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/TypedPublisherTest.java`
  - Add to existing TypedPublisherTest class
  - Test: POST to `/typed-publisher/publish-cloudevents` with explicit metadata → verify 200 response with ce-* headers in response
  - Test: POST to `/typed-publisher/publish-cloudevents` with null metadata → verify auto-generation
  - Note: Response includes ce-id and ce-time so client can verify auto-generation

---

## Phase 5: Polish & Integration Validation

### Error Handling & Edge Cases

- [x] T023 [P] Handle edge cases in TypedPayloadEncoder
  - Test: Very large byte arrays (base64 expansion ~33%; test with 10MB array)
  - Test: Arrays with null elements (should throw appropriate exception)
  - Test: Empty arrays (should encode as empty string)
  - Test: Unicode strings (should encode correctly as UTF-8)
  - Test: Special characters in NATS subject (if subject becomes dynamic in future MVP)

- [x] T024 [US2] Validate CloudEvents header compliance
  - Test: ce-specversion is always "1.0"
  - Test: ce-type format validation (should be non-empty string)
  - Test: ce-source format validation (should be non-empty string)
  - Test: ce-id is valid UUID v4 format
  - Test: ce-time is valid ISO 8601 format
  - Test: No ce-* header collisions with user data

- [x] T025 Document error messages for developers in `specs/002-typed-publisher/quickstart.md`
  - Null publishing: "Cannot publish null object"
  - Serialization failure: "Failed to serialize {ClassName}: {cause}"
  - Missing Jackson: Build-time error (at compile time, not runtime)

### Manual Integration Testing

- [x] T026 [US1] Manual test: Publish primitive types with docker-compose
  - Start NATS: `docker-compose up nats`
  - Subscribe: `nats sub test`
  - Publish via REST: `curl -X POST http://localhost:8080/typed-publisher/publish -H "Content-Type: application/json" -d '{"objectType":"java.lang.String","payload":"hello"}'`
  - Verify: "hello" appears in subscriber terminal
  - Repeat for Integer, byte[], etc.

- [x] T027 [US1] Manual test: Publish domain objects with docker-compose
  - Create test POJO: `Order` with @RegisterForReflection
  - Publish via REST with Order payload
  - Verify JSON appears in subscriber: `{"orderId":"ORD-123",...}`

- [x] T028 [US2] Manual test: Publish CloudEvents with header inspection
  - Subscribe with headers: `nats sub test --raw`
  - Publish CloudEvents via REST
  - Verify ce-* headers appear in subscriber output
  - Verify ce-id is UUID and ce-time is ISO 8601

- [x] T029 [US2] Manual test: CloudEvents with auto-generated metadata
  - Publish with null ceType and ceSource
  - Verify ce-type equals fully-qualified class name
  - Verify ce-source equals hostname or app identifier

### Documentation Finalization

- [x] T030 Update CLAUDE.md with MVP 002 information
  - Add: "Active Technologies: Java 21, Quarkus 3.27.0, Jackson 2.x, NATS JetStream"
  - Add: "Recent Changes: MVP 002 - Typed NatsPublisher with CloudEvents support"
  - Add: "Key Classes: TypedPayloadEncoder, CloudEventsHeaders, NatsPublisher<T>"

- [x] T031 Verify all code follows CLAUDE.md guidelines
  - Constructor injection: REQUIRED (no @Inject field injection in runtime code)
  - AssertJ assertions: REQUIRED in tests (no JUnit assertions)
  - No jakarta.ws.rs.core.Response: Use POJOs in REST endpoints
  - No Awaitility needed: No Thread.sleep() in tests

- [x] T032 Final verification build: `./mvnw clean install`
  - All modules compile successfully
  - All unit tests pass
  - No warnings or errors

---

## Task Summary by User Story

### User Story 1: Typed Publishing (P1)
- **Core Tasks**: T001-T014
- **Task Count**: 14 tasks
- **Duration**: ~2-3 days (single developer)
- **Parallelizable**: T009, T013 can run in parallel
- **MVP Scope**: Complete this phase for minimum viable product

### User Story 2: CloudEvents (P2)
- **Core Tasks**: T015-T024
- **Task Count**: 10 tasks
- **Duration**: ~2-3 days (after US1 complete)
- **Parallelizable**: T016, T018, T019, T020, T021, T022 can run in parallel
- **Dependency**: Requires US1 (TypedPayloadEncoder, NatsPublisher<T>)

### Polish & Integration (Cross-Cutting)
- **Core Tasks**: T025-T032
- **Task Count**: ~8 tasks
- **Duration**: ~1-2 days (after US1 and US2 complete)
- **Sequential**: Tests and documentation depend on implementations

---

## Implementation Strategy

### MVP Scope (Minimum Viable Product)
Complete **Phases 1-3 (US1)** for a working typed publisher:
- Setup project dependencies
- Implement TypedPayloadEncoder (primitives, bytes as base64, arrays, Jackson fallback)
- Extend NatsPublisher<T> with publish() method
- Create TypedPublisherResource endpoint for testing
- Manual docker-compose testing

**Estimated Duration**: 3-5 days (single developer)
**Delivers**: Type-safe JSON publishing without CloudEvents

### Full Feature Scope (Complete MVP 002)
Add **Phases 4-5 (US2 + Polish)**:
- CloudEvents support with auto-generated headers
- publishCloudEvent() method
- Integration testing with header inspection
- Documentation and error handling

**Estimated Duration**: 5-8 days total (single developer)
**Delivers**: Complete typed publisher with CloudEvents spec 1.0 compliance

---

## Testing Approach

**Per MVP 002 Spec**: Manual testing only (no automated integration tests)

### Unit Testing
- TypedPayloadEncoder: Direct tests with AssertJ
- CloudEventsHeaders: Header generation validation
- NatsPublisher<T>: Mock NatsConnectionManager; verify encoder resolution

### Integration Testing (Manual)
- docker-compose start NATS broker
- NATS CLI `nats sub test` to inspect messages
- HTTP REST calls via curl to `/typed-publisher/publish` and `/typed-publisher/publish-cloudevents`
- Visual verification of JSON and ce-* headers

### No Automated Integration Tests
- Per user feedback: "Same manual testing with docker compose and nats cli for now. keep it simple and minimal"
- Avoids Awaitility; keeps test complexity low
- Enables rapid MVP delivery

---

## Notes for Implementation

1. **Byte Encoding CRITICAL**: ALWAYS base64 for byte types; NEVER raw binary
2. **Constructor Injection REQUIRED**: Per CLAUDE.md; update NatsPublisher constructor for ObjectMapper
3. **Subject Hardcoded**: "test" is hardcoded; no @NatsSubject annotation (deferred to MVP 003)
4. **Type Safety**: Generics erased at runtime; use `payload.getClass()` for encoder resolution
5. **Error Messages**: User-friendly and documented in quickstart.md
6. **CloudEvents Compliance**: Binary content mode with ce-* headers; no structured envelope
