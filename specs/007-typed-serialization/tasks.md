# Implementation Tasks: Typed Serialization with Jackson Integration

**Feature**: 007-typed-serialization
**Branch**: `007-typed-serialization`
**Date Generated**: 2025-10-27
**Plan Reference**: [plan.md](./plan.md) | [Spec](./spec.md) | [Contracts](./contracts/)

---

## Executive Summary

This document defines implementation tasks for the **Typed Serialization with Jackson Integration** feature. The feature simplifies message serialization/deserialization by supporting **only Jackson-compatible types** (POJOs, records, generics), eliminating "magic" through explicit type boundaries.

**Implementation Scope**:
- 4 user stories (P1 + P2 priorities)
- 45 implementation tasks
- ~2-3 weeks estimated effort (serial execution)
- Independent testing per story (can demo MVP after US1)

**MVP Strategy**: Complete User Story 1 (Publish and Subscribe to Typed Messages) to deliver core value. US2-4 enhance reliability and usability.

---

## Task Phases Overview

| Phase | Title | Duration | Dependencies | MVP? |
|-------|-------|----------|--------------|------|
| Phase 1 | Setup & Verification | 1-2 hours | None | Yes |
| Phase 2 | Foundational: Type Validation & Error Handling | 4-6 hours | Phase 1 | Yes |
| Phase 3 | **User Story 1 (P1)**: Publish and Subscribe to Typed Messages | 6-8 hours | Phase 2 | **Yes** ✅ |
| Phase 4 | **User Story 2 (P1)**: Jackson-Only Type Support with Clear Errors | 4-6 hours | Phase 3 | Yes |
| Phase 5 | **User Story 3 (P2)**: Jackson Annotations Support | 2-3 hours | Phase 4 | No |
| Phase 6 | **User Story 4 (P2)**: Documentation and Examples | 4-5 hours | Phase 5 | No |
| Phase 7 | Polish, Testing, and Integration | 6-8 hours | Phase 6 | Yes |

---

## Phase 1: Setup & Verification

**Goal**: Verify project structure and dependencies are ready for implementation.

**Independent Test Criteria**:
- Maven build succeeds without errors
- All dependencies resolved (Jackson, Quarkus, NATS client)
- Existing 006-typed-subscriber feature builds and tests pass
- Project structure validated per plan.md

### Tasks

- [x] T001 Verify project structure matches plan (runtime + deployment + integration-tests modules) in `pom.xml` and directory layout
- [x] T002 Verify Jackson Databind dependency exists in `runtime/pom.xml` with version 2.15.x or later
- [x] T002 Verify Quarkus version is 3.27.0+ in parent `pom.xml`
- [x] T003 Verify existing 006-typed-subscriber tests pass: `./mvnw -pl integration-tests clean test`
- [x] T004 Verify existing NatsPublisher class exists in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisher.java`
- [x] T005 Verify existing MessageDeserializer class exists in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/MessageDeserializer.java`
- [x] T006 Verify existing TypedPayloadEncoder class exists in `runtime/src/main/java/org/mjelle/quarkus/easynats/TypedPayloadEncoder.java`
- [x] T007 Build entire project and confirm no errors: `./mvnw clean install -DskipTests`

---

## Phase 2: Foundational - Type Validation & Error Handling

**Goal**: Implement core type validation infrastructure and error classes that all user stories depend on.

**Independent Test Criteria**:
- Type validation correctly identifies valid Jackson-compatible types
- Type validation correctly rejects primitives, arrays, and types without no-arg constructors
- Clear error messages guide users to wrapper pattern
- SerializationException and DeserializationException properly defined
- Build succeeds with no compilation errors

### Tasks

- [x] T008 Create `TypeValidator` class in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/TypeValidator.java` with methods:
  - `validate(Class<?> type): TypeValidationResult`
  - `validatePrimitiveType(Class<?> type): boolean`
  - `validateArrayType(Class<?> type): boolean`
  - `validateNoArgConstructor(Class<?> type): boolean`
  - Unit test in `runtime/src/test/java/org/mjelle/quarkus/easynats/runtime/subscriber/TypeValidatorTest.java`

- [x] T009 Create `TypeValidationResult` class in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/TypeValidationResult.java` with fields:
  - `valid: boolean`
  - `typeName: String`
  - `errorMessage: String`
  - `errorType: ValidationErrorType`
  - Enum `ValidationErrorType` with: PRIMITIVE_TYPE, ARRAY_TYPE, MISSING_NO_ARG_CTOR, UNRESOLVABLE_GENERIC, JACKSON_ERROR, CUSTOM_ERROR

- [x] T010 Create or update `SerializationException` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/SerializationException.java`:
  - Extends Exception (checked)
  - Constructor with message only
  - Constructor with message + cause

- [x] T011 Create or update `DeserializationException` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/DeserializationException.java`:
  - Extends Exception (checked)
  - Constructor with message only
  - Constructor with message + cause

- [x] T012 Create context classes in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/`:
  - `SerializationContext.java`: Holds MessageType + payload + timestamp
  - `DeserializationContext.java`: Holds MessageType + rawPayload + timestamp
  - Unit tests: `*ContextTest.java` in `runtime/src/test/java/...`

- [x] T013 Create `MessageType<T>` class in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/MessageType.java` with:
  - Generic type parameter T
  - Fields: `rawClass`, `jacksonType`, `validationResult`, `objectMapper`
  - Constructor taking all fields
  - Getters for each field
  - Validation invariants verified in constructor

- [x] T014 [P] Update TypeValidator to use Jackson introspection for type validation in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/TypeValidator.java`:
  - Call `ObjectMapper.getTypeFactory().constructType(type)` to validate type structure
  - Catch exceptions and categorize errors (UNRESOLVABLE_GENERIC, JACKSON_ERROR)
  - Generate clear error messages directing users to wrapper pattern
  - Unit test all validation error cases in TypeValidatorTest

- [x] T015 [P] Create error message generator utility in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/ErrorMessageFormatter.java`:
  - Method: `formatPrimitiveTypeError(Class<?> type): String`
  - Method: `formatArrayTypeError(Class<?> type): String`
  - Method: `formatMissingNoArgCtorError(Class<?> type): String`
  - Method: `formatUnresolvableGenericError(Class<?> type): String`
  - Each returns clear message with wrapper pattern example
  - Unit test: `runtime/src/test/java/.../ErrorMessageFormatterTest.java`

---

## Phase 3: User Story 1 - Publish and Subscribe to Typed Messages (P1)

**Goal**: Users can publish and subscribe to typed messages without manual serialization. Core value proposition.

**Story**: A library user defines a message type as a simple class (POJO or record) and publishes/subscribes to instances of that type. The library automatically handles serialization and deserialization.

**Independent Test Criteria** (all must pass for MVP):
- POJO with no-arg constructor can be published via TypedPublisher
- POJO with no-arg constructor can be subscribed to via @NatsSubscriber
- Published message arrives as correct deserialized type on subscriber
- No manual serialization code required by user
- Java record types work as well
- Deserialization errors logged with target type and raw payload

### Tasks

- [x] T016 [US1] Simplify `TypedPayloadEncoder` in `runtime/src/main/java/org/mjelle/quarkus/easynats/TypedPayloadEncoder.java`:
  - Remove all native type handling (primitives, arrays, byte[], String[], etc.)
  - Keep only method: `encodeWithJackson(Object payload, ObjectMapper mapper): byte[]`
  - Serialize using: `objectMapper.writeValueAsBytes(payload)`
  - Catch `JsonProcessingException` and wrap in `SerializationException` with type name
  - Unit test: `runtime/src/test/java/.../TypedPayloadEncoderTest.java` - test POJO, record, generic types, error cases

- [x] T017 [US1] Simplify `MessageDeserializer` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/subscriber/MessageDeserializer.java`:
  - Remove all native type handling (primitives, arrays, byte[], String[], etc.)
  - Keep only Jackson deserialization: `deserialize(DeserializationContext<T>): T`
  - Use: `objectMapper.readValue(rawPayload, messageType.getJacksonType())`
  - Catch `IOException` or `JsonMappingException` and wrap in `DeserializationException`
  - Log error with: target type, raw payload (first 1000 chars), root cause
  - Unit test: `runtime/src/test/java/.../MessageDeserializerTest.java` - test POJO, record, error cases

- [x] T018 [US1] Update `NatsPublisher.encodePayload()` in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisher.java`:
  - Modify internal method to use Jackson-only encoding: `TypedPayloadEncoder.encodeWithJackson()`
  - Public API (`publish(T)` and `publish(String, T)`) **remains unchanged**
  - Remove any native type handling from encoding path
  - Update error handling to throw `SerializationException` with clear message

- [x] T019 [US1] Update `DefaultMessageHandler` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/handler/DefaultMessageHandler.java`:
  - Use new Jackson-only `MessageDeserializer`
  - Pass `DeserializationContext` instead of just payload
  - Catch `DeserializationException` and log with context (type, raw payload)
  - Automatically NAK message on deserialization failure
  - Do NOT invoke subscriber method if deserialization fails

- [x] T020 [US1] Create integration test model: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/model/OrderData.java`
  - POJO with no-arg constructor
  - Fields: `id: String`, `customerName: String`, `amount: BigDecimal`
  - Include both no-arg and convenience constructors

- [x] T021 [US1] Create integration test listener: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/OrderListener.java`
  - Singleton bean with `@NatsSubscriber` method
  - Subject: "orders", consumer: "order-processor"
  - Method: `handleOrder(OrderData order)`
  - Store last received order for testing via REST endpoint

- [x] T022 [US1] Create integration test resource: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/OrderPublisherResource.java`
  - REST endpoints for publishing orders
  - Inject `TypedPublisher<OrderData>` with `@NatsSubject("orders")`
  - POST `/publish/order` to publish an order from query params

- [x] T023 [US1] Create integration test resource: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/OrderSubscriberResource.java`
  - REST endpoints for retrieving received messages
  - GET `/subscribe/last-order` to retrieve last received OrderData
  - Use static accessor from NatsTestUtils or instance field

- [x] T024 [US1] Create JVM integration test: `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/TypedSerializationTest.java`
  - `@QuarkusTest` annotation
  - Test 1: `testPublishAndSubscribeWithPOJO()` - Publish OrderData via REST, verify received on subscriber
  - Test 2: `testPublishAndSubscribeWithRecord()` - Create record type, publish and subscribe
  - Test 3: `testPublishAndSubscribeWithGenericType()` - List<OrderData> or Map<String, OrderData>
  - Test 4: `testDeserializationErrorLogsWithTypeInfo()` - Send malformed JSON, verify error logged
  - Use Awaitility for async assertions (up to 5 seconds, poll every 100ms)
  - Use RestAssured for REST calls
  - Use AssertJ for assertions

- [x] T025 [US1] Create native image integration test: `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/TypedSerializationIT.java`
  - `@QuarkusIntegrationTest` annotation
  - Extends `TypedSerializationTest` (reuses all test methods in native context)

- [x] T026 [US1] Build and run JVM tests: `./mvnw -pl integration-tests clean test -Dtest=TypedSerializationTest`
  - Verify all 4 test cases pass
  - Verify logs show error context (type, payload) for malformed JSON test

- [x] T027 [US1] Verify published message structure in NATS:
  - Publish OrderData via REST endpoint
  - Inspect raw NATS message (use nats CLI or logs)
  - Verify message is JSON representation of OrderData
  - Verify CloudEvents headers are present (internal detail)

---

## Phase 4: User Story 2 - Jackson-Only Type Support with Clear Errors (P1)

**Goal**: Users understand which types are supported and receive clear guidance when using unsupported types.

**Story**: A library user attempts to use unsupported message types (e.g., primitives, arrays, classes without no-arg constructor). The library clearly rejects these with messages explaining what types are supported and how to wrap unsupported types.

**Independent Test Criteria** (all must pass):
- Primitive types (int, long, double) rejected at publisher/subscriber registration with clear error
- Array types (int[], String[]) rejected with clear error
- Types without no-arg constructor rejected with clear error
- Error messages include wrapper pattern examples
- Errors fail fast (application startup fails if type invalid)
- Error guidance directs users to documentation

### Tasks

- [ ] T028 [US2] Implement type validation at publisher registration in `NatsPublisher`:
  - Extract generic type parameter T when publisher bean is created
  - Call `TypeValidator.validate(T)`
  - If invalid: Throw `IllegalArgumentException` with clear error message
  - If valid: Cache `MessageType<T>` in publisher bean
  - Update class to use `MessageType<T>` instead of raw type

- [ ] T029 [US2] Implement type validation at subscriber registration in `DefaultMessageHandler`:
  - Extract type parameter T from @NatsSubscriber method parameter when subscriber is registered
  - Call `TypeValidator.validate(T)`
  - If invalid: Throw `IllegalArgumentException` with clear error message and fail startup
  - If valid: Cache `MessageType<T>` for use during deserialization

- [ ] T030 [US2] Add unit test for primitive type rejection in `TypeValidatorTest`:
  - Test: `testPrimitiveIntRejected()` - Verify `int` rejected with message about wrapper
  - Test: `testPrimitiveLongRejected()` - Verify `long` rejected
  - Test: `testPrimitiveDoubleRejected()` - Verify `double` rejected
  - All should return `TypeValidationResult` with `valid=false` and error type `PRIMITIVE_TYPE`

- [ ] T031 [US2] Add unit test for array type rejection in `TypeValidatorTest`:
  - Test: `testArrayIntRejected()` - Verify `int[]` rejected with message about wrapper
  - Test: `testArrayStringRejected()` - Verify `String[]` rejected
  - All should return `TypeValidationResult` with `valid=false` and error type `ARRAY_TYPE`

- [ ] T032 [US2] Add unit test for missing no-arg constructor in `TypeValidatorTest`:
  - Create test class `TypeWithoutNoArgCtor` (constructor only takes String)
  - Test: `testMissingNoArgCtorRejected()` - Verify rejected with message suggesting adding no-arg ctor
  - Should return `TypeValidationResult` with error type `MISSING_NO_ARG_CTOR`

- [ ] T033 [US2] Create integration test model: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/model/InvalidType.java`
  - Type without no-arg constructor for testing rejection
  - Constructor takes required parameters

- [ ] T034 [US2] Create integration test model: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/model/UnsupportedType.java`
  - Primitive wrapper (Integer) used as direct subscription type for testing rejection

- [ ] T035 [US2] Add test to TypedSerializationTest: `testPrimitiveTypeRejectedAtRegistration()`
  - Attempt to inject `TypedPublisher<Integer>` with @NatsSubject
  - Verify application fails to start with clear error message about wrapper pattern

- [ ] T036 [US2] Add test to TypedSerializationTest: `testArrayTypeRejectedAtRegistration()`
  - Attempt to inject `TypedPublisher<String[]>` with @NatsSubject
  - Verify application fails to start with clear error message

- [ ] T037 [US2] Add test to TypedSerializationTest: `testMissingNoArgCtorRejectedAtRegistration()`
  - Attempt to inject `TypedPublisher<InvalidType>` where InvalidType has no no-arg ctor
  - Verify application fails to start with clear error message

- [ ] T038 [US2] Add test to TypedSerializationTest: `testSubscriberTypeValidationFailsFast()`
  - Create @NatsSubscriber method with unsupported type parameter
  - Verify application fails to start at subscriber registration

- [ ] T039 [US2] Run integration tests and verify error messages are clear: `./mvnw -pl integration-tests clean test -Dtest=TypedSerializationTest`
  - All rejection tests pass
  - Error messages visible in test output

- [ ] T040 [US2] Verify error messages include wrapper pattern examples in `ErrorMessageFormatterTest`:
  - Test: `testPrimitiveTypeErrorIncludes WrappingExample()`
  - Test: `testArrayTypeErrorIncludesWrappingExample()`
  - Verify each error message contains POJO example and explanation

---

## Phase 5: User Story 3 - Jackson Annotations Integration (P2)

**Goal**: Verify the library doesn't break standard Jackson annotations and guides users to leverage them for customization.

**Story**: A library user has a message type with fields needing custom JSON handling. Instead of library-specific APIs, users leverage standard Jackson annotations (`@JsonProperty`, `@JsonIgnore`, `@JsonDeserialize`, `@JsonSerialize`). The library passes these types to Jackson unchanged.

**Critical Clarification**: This library doesn't "implement" Jackson annotation support - Jackson already does that. Our responsibility is to:
1. Ensure CloudEvents wrapping doesn't interfere with annotations
2. Guide users to annotations in error messages
3. Document that annotations work transparently

**Independent Test Criteria**:
- Serialized JSON respects `@JsonProperty` (fields use renamed JSON keys)
- Serialized JSON respects `@JsonIgnore` (annotated fields missing from JSON)
- Deserialized objects respect `@JsonDeserialize` (custom deserializers invoked)
- CloudEvents wrapping doesn't interfere with Jackson's annotation processing
- Error messages suggest annotations for customization needs
- Documentation shows Jackson annotation examples work
- No regression when using annotated types

### Tasks

- [ ] T041 [US3] Create annotated test model: `integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/model/AnnotatedOrderData.java`
  - Field with `@JsonProperty("order_id")` for renamed field in JSON
  - Field with `@JsonIgnore` to exclude from serialization
  - Regular field for baseline comparison
  - Document: "This shows Jackson annotations work with the library"

- [ ] T042 [US3] Add smoke test to TypedSerializationTest: `testAnnotatedTypesSerializationRoundtrip()`
  - Serialize AnnotatedOrderData → Jackson processes annotations → JSON produced
  - Verify serialized JSON: @JsonProperty field uses renamed key, @JsonIgnore field absent
  - Wrap JSON in CloudEvents → transmit → unwrap to get JSON
  - Deserialize JSON → Jackson processes annotations → object reconstructed correctly
  - Verify deserialized object matches original (accounting for @JsonIgnore fields being default values)
  - Purpose: Verify CloudEvents wrapping doesn't interfere with Jackson's annotation processing
  - **Note**: We don't test that Jackson's annotations work (Jackson's own tests do that); we test that wrapping/unwrapping in CloudEvents doesn't break them

- [ ] T043 [US3] Update error messages to suggest Jackson annotations:
  - When type fails validation, suggest using `@JsonProperty` for field mapping
  - When deserialization fails, suggest `@JsonDeserialize` for custom logic
  - When serialization fails, suggest `@JsonIgnore` for transient fields
  - Files to update: ErrorMessageFormatter.java

- [ ] T044 [US3] Create documentation: `specs/007-typed-serialization/JACKSON_ANNOTATIONS_GUIDE.md`
  - Section 1: "Standard Jackson Annotations Work" - Library delegates to Jackson, so all standard annotations work transparently
  - Section 2: "@JsonProperty" - Customize JSON field names (example: field `id` → JSON key `order_id`)
  - Section 3: "@JsonIgnore" - Exclude fields from JSON serialization (example: password field not sent)
  - Section 4: "@JsonDeserialize" - Custom deserialization logic (example: date parsing with custom format)
  - Section 5: "@JsonSerialize" - Custom serialization logic (example: custom date formatting)
  - Include clear note: "These are standard Jackson annotations. The library doesn't add anything - it just uses Jackson's ObjectMapper directly, so all Jackson features work out of the box"

- [ ] T045 [US3] Run smoke test: `./mvnw -pl integration-tests clean test -Dtest=TypedSerializationTest#testAnnotatedTypesSerializationRoundtrip`
  - Verify annotated types serialize/deserialize correctly through library
  - JSON carries annotations' effects (renamed fields, missing ignored fields)
  - CloudEvents wrapping doesn't interfere with Jackson annotation processing
  - No regression when using annotated types

- [ ] T046 [US3] Review Phase 5 completion:
  - Smoke test passes (CloudEvents wrapping doesn't break annotations)
  - Error messages guide users to annotations for customization
  - Documentation clearly states library delegates to Jackson
  - Verify: "Standard Jackson annotations work transparently because library uses ObjectMapper directly"

---

## Phase 6: User Story 4 - Documentation and Examples (P2)

**Goal**: Provide clear guidance on supported types, error troubleshooting, and wrapper patterns.

**Story**: Library documentation explicitly lists what makes a type Jackson-compatible and provides examples of supported/unsupported patterns and how to create wrappers.

**Independent Test Criteria**:
- Documentation covers Jackson requirements
- Examples are accurate and runnable
- Wrapper pattern is clearly explained with working examples
- Troubleshooting guide covers common errors

### Tasks

- [ ] T047 [US4] Create documentation file: `specs/007-typed-serialization/JACKSON_COMPATIBILITY_GUIDE.md`
  - Section 1: "What Types Are Supported" with list of supported types
  - Section 2: "POJOs with No-Arg Constructor" with example
  - Section 3: "Java Records" with example
  - Section 4: "Generic Types" with List<T>, Map<K,V> examples
  - Section 5: "Jackson Annotations" with @JsonProperty, @JsonIgnore, @JsonDeserialize examples
  - Section 6: "What Types Are NOT Supported" with primitives, arrays, examples

- [ ] T048 [US4] Create documentation file: `specs/007-typed-serialization/WRAPPER_PATTERN.md`
  - Title: "Wrapping Unsupported Types"
  - Introduction: Explain when wrapping is needed
  - Example 1: Wrap primitive int in IntValue POJO
  - Example 2: Wrap array String[] in StringList POJO
  - Example 3: Wrap type without no-arg constructor in wrapper
  - Code examples with full implementation
  - Best practices section

- [ ] T049 [US4] Create documentation file: `specs/007-typed-serialization/ERROR_TROUBLESHOOTING.md`
  - Error: "Type 'int' is not supported" → Solution with wrapper example
  - Error: "Missing no-arg constructor" → Solution with code fix
  - Error: "Type has unresolvable generic parameter" → Solution with concrete type
  - Error: "Failed to deserialize: Unexpected character" → Solution with JSON validation
  - Error: "Failed to serialize: Infinite recursion" → Solution with @JsonIgnore
  - Each error includes example code and explanation

- [ ] T050 [US4] Update existing quickstart (already created): `specs/007-typed-serialization/quickstart.md`
  - Verify covers all user scenarios from spec
  - Add link to JACKSON_COMPATIBILITY_GUIDE.md
  - Add link to WRAPPER_PATTERN.md
  - Add link to ERROR_TROUBLESHOOTING.md

- [ ] T051 [US4] Create README.md for easy-nats project (if not exists): `README.md`
  - Link to Typed Serialization feature documentation
  - Quick start example for typed messages
  - Links to all feature documentation

- [ ] T052 [US4] Verify all documentation examples compile and run:
  - Extract code examples from documentation
  - Create test POJOs and verify they can be instantiated
  - Verify example JSON serialization/deserialization works
  - Manual testing or automated doc test (doctest style)

---

## Phase 7: Polish, Testing, and Integration

**Goal**: Comprehensive testing, performance validation, and production readiness.

**Independent Test Criteria**:
- All unit tests pass (runtime module)
- All integration tests pass (JVM + native image)
- Performance benchmarks acceptable
- No regressions in existing features
- Documentation complete and reviewed
- Code style and quality checks pass

### Tasks

- [ ] T053 Run all unit tests: `./mvnw -pl runtime clean test`
  - All TypeValidator, MessageDeserializer, TypedPayloadEncoder, etc. tests pass
  - Coverage > 80% for new code

- [ ] T054 Run all integration tests (JVM): `./mvnw -pl integration-tests clean test`
  - TypedSerializationTest passes all 45 test cases
  - No test failures or skipped tests
  - Logs show expected behavior (error messages, successful ACK/NAK)

- [ ] T055 Run full native image integration tests: `./mvnw clean install -Pit`
  - TypedSerializationIT passes all test methods (inherited from TypedSerializationTest)
  - Native image compilation succeeds
  - No native-specific issues

- [ ] T056 [P] Performance test: No serialization latency regression
  - Compare publish latency (before/after feature)
  - Compare deserialization latency (before/after feature)
  - Acceptable: < 10% latency increase
  - Document results in `PERFORMANCE_NOTES.md`

- [ ] T057 Verify no regressions in existing features:
  - 001-basic-publisher-api tests pass
  - 002-typed-publisher tests pass
  - 004-nats-subscriber-mvp tests pass
  - 005-transparent-cloudevents tests pass
  - 006-typed-subscriber tests pass
  - Run: `./mvnw clean test`

- [ ] T058 Code quality checks:
  - Run `./mvnw verify` (checkstyle, spotbugs, etc. if configured)
  - Fix any code style violations
  - Verify no critical issues

- [ ] T059 Build final artifact: `./mvnw clean install`
  - Verify runtime JAR size < 500 KB (Constitution Principle II)
  - Report final JAR size in build output

- [ ] T060 Document breaking changes (if any):
  - Update CHANGELOG.md with feature summary
  - List any API changes (should be minimal - internal only)
  - Note removal of native type handling from MessageDeserializer/TypedPayloadEncoder

- [ ] T061 Create migration guide if needed: `MIGRATION_007_TYPED_SERIALIZATION.md`
  - For users upgrading from 006-typed-subscriber
  - Note: Only Jackson-compatible types now supported
  - Show wrapper pattern for previously supported types

- [ ] T062 [P] Final integration test: End-to-end publish/subscribe with all variations
  - POJO with no-arg ctor
  - Java record
  - Generic type (List<T>)
  - Type with Jackson annotations
  - Error case (malformed JSON)
  - All 5 scenarios in single test flow
  - Verify success and failure paths

- [ ] T063 Final documentation review:
  - Verify quickstart.md is accurate
  - Verify JACKSON_COMPATIBILITY_GUIDE.md is complete
  - Verify WRAPPER_PATTERN.md has working examples
  - Verify ERROR_TROUBLESHOOTING.md covers common cases
  - Update plan.md with completion date and summary

- [ ] T064 Prepare PR description:
  - Summary of feature changes
  - Breaking changes (if any)
  - Metrics: lines added/removed, test coverage
  - Review checklist

---

## Dependency Graph

```
Phase 1 (Setup)
  ↓
Phase 2 (Type Validation & Error Handling)
  ├─ T008-T015
  ├─ BLOCKING for Phase 3, 4, 5
  ↓
Phase 3 (US1: Publish/Subscribe Core)
  ├─ T016-T027
  ├─ Dependent on Phase 2
  ├─ BLOCKING for Phase 4 (error testing needs US1 working)
  ↓
Phase 4 (US2: Jackson-Only with Clear Errors)
  ├─ T028-T040
  ├─ Dependent on Phase 3
  ├─ Can run parallel with Phase 5-6 after Phase 4 starts
  ↓
Phase 5 (US3: Jackson Annotations)
  ├─ T041-T046
  ├─ Dependent on Phase 3 (US1 must work)
  ├─ Can run parallel with Phase 6 after Phase 5 starts
  ↓
Phase 6 (US4: Documentation)
  ├─ T047-T052
  ├─ Can run parallel with Phase 5 (no code dependencies)
  ↓
Phase 7 (Polish & Integration)
  ├─ T053-T064
  ├─ Dependent on all previous phases
```

## Parallel Execution Opportunities

**During Phase 3** (after T015 complete):
- T016-T018 (TypedPayloadEncoder, MessageDeserializer, NatsPublisher changes) - can parallelize
- T019 (DefaultMessageHandler update) - depends on T016-T017

**During Phase 4** (after Phase 3 complete):
- T030-T032 (Unit tests for rejection cases) - can parallelize
- T033-T038 (Integration test models and tests) - can parallelize after unit tests

**During Phase 5-6** (can run in parallel):
- Phase 5 (T041-T046): Jackson annotations tests
- Phase 6 (T047-T052): Documentation
- No dependencies between these phases

**During Phase 7** (after Phase 6 complete):
- T053-T057 (Different test suites) - can parallelize

---

## Success Criteria Mapping

| Spec Criterion | Implementation Tasks | Validation |
|--------|----------------------|-----------|
| SC-001: Users can publish/subscribe to types without manual serialization | T016-T027 | TypedSerializationTest passes |
| SC-002: Unsupported types rejected with clear errors | T028-T040 | Rejection tests pass, error messages visible |
| SC-003: Deserialization errors logged with context | T024-T027 | Error logging test passes |
| SC-004: Documentation includes 3+ examples of supported types + 2+ wrapper patterns | T047-T050 | Documentation complete |
| SC-005: Users with Jackson annotations can reuse types unmodified | T041-T046 | Annotation tests pass |
| SC-006: New developers can publish/subscribe following docs | T047-T052 | Documentation review + manual verification |

---

## Notes

- **Jackson Version**: Ensure Jackson 2.15.x or later (supports Java 21 records)
- **Test Database**: Not needed (messaging system, no persistence)
- **Performance**: Target <10% latency increase vs. existing implementation
- **Native Image**: All tests must pass in both JVM and native contexts
- **Error Messages**: All error messages must be ≤ 1000 characters for logging efficiency

---

## Sign-Off Checklist

- [ ] All tasks completed per specification
- [ ] All unit tests pass (runtime module, >80% coverage)
- [ ] All JVM integration tests pass
- [ ] All native image tests pass
- [ ] No regressions in existing features
- [ ] Documentation complete and reviewed
- [ ] Build succeeds with clean output
- [ ] PR ready for review
