# Implementation Tasks: Typed Subscriber with @NatsSubscriber Annotation

**Feature**: 006-typed-subscriber
**Branch**: `006-typed-subscriber`
**Date Generated**: 2025-10-27
**Spec Reference**: [spec.md](spec.md) | [plan.md](plan.md) | [data-model.md](data-model.md)

---

## Overview

This tasks list implements the **Typed Subscriber with @NatsSubscriber Annotation** feature—extending the basic subscriber infrastructure from 004 with CloudEvents 1.0 unwrapping and typed message deserialization.

**Total Tasks**: 28
**Phases**: 5 (Setup + Foundational + 2 User Stories + Polish)

### Task Organization

- **Phase 1**: Setup (3 tasks)
- **Phase 2**: Foundational - Shared Infrastructure (5 tasks)
- **Phase 3**: User Story 1 - Typed Message Deserialization (9 tasks)
- **Phase 4**: User Story 2 - CloudEvents Unwrapping (7 tasks)
- **Phase 5**: User Story 3 - Auto-Inject Subscriber Bean (2 tasks)
- **Phase 6**: Polish & Cross-Cutting Concerns (2 tasks)

### MVP Scope

**Recommended MVP** (Phase 1-3): User Story 1 (core typed deserialization). This delivers the foundational typed parameter support and is independently testable.

**Extended MVP** (Phase 1-4): Add User Story 2 (CloudEvents unwrapping) for complete CloudEvent compliance.

### Parallel Execution Opportunities

- **Phase 3 Tasks**: T007-T009, T012-T014 can run in parallel (different files, no interdependencies)
- **Phase 4 Tasks**: T016-T018 can run in parallel (CloudEvent validation independent of deserialization)
- **Phase 5 Tasks**: T025, T026 can run in parallel after Phase 2 completion

---

## Phase 1: Setup

Initialize project structure and verify extension framework integration.

- [ ] T001 Review 004-nats-subscriber-mvp infrastructure and confirm extension processor pattern
  - File: `deployment/src/main/java/io/nats/ext/deployment/processor/SubscriberProcessor.java` (existing)
  - Verify: Annotation discovery, bean wiring, build-time processing works in baseline
  - Output: Documented understanding of inheritance chain (004 → 006)

- [ ] T002 Add Jackson and CloudEvents SDK dependencies to runtime module pom.xml
  - File: `runtime/pom.xml`
  - Action: Verify Jackson Databind is present (should be from 005-transparent-cloudevents). Confirm CloudEvents SDK 1.0 is available.
  - Rationale: No new dependencies are expected, as these are outlined in the technical context of plan.md.
  - Note: Fail build if Jackson or CloudEvents missing

- [ ] T003 Create package structure for new subscriber classes
  - File: Create directories `runtime/src/main/java/io/nats/ext/subscriber/` (if not present)
  - File: Create test directories `runtime/src/test/java/io/nats/ext/subscriber/`
  - Action: mkdir -p with proper Java package structure

---

## Phase 2: Foundational Infrastructure

Implement core decoding and unwrapping utilities that both user stories depend on.

- [ ] T004 Create CloudEventException custom exception class
  - File: `runtime/src/main/java/io/nats/ext/subscriber/CloudEventException.java`
  - Content: Extend RuntimeException, used for CloudEvent validation failures
  - Constructor overloads: `(String message)`, `(String message, Throwable cause)`

- [ ] T005 Implement CloudEventUnwrapper utility with CloudEvents 1.0 binary-mode validation
  - File: `runtime/src/main/java/io/nats/ext/subscriber/CloudEventUnwrapper.java`
  - Method signature: `public static byte[] unwrapData(io.nats.client.Message message) throws CloudEventException`
  - Validation logic:
    1. Check required headers present: `ce-specversion`, `ce-type`, `ce-source`, `ce-id`
    2. Validate `ce-specversion == "1.0"` (reject other versions)
    3. Verify binary-mode (attributes NOT in payload JSON)
    4. Extract message data payload and return as byte[]
    5. Throw CloudEventException if validation fails with clear error message
  - Use CloudEvents SDK for header parsing (if available) or manual header inspection
  - Error messages must include missing header name

- [ ] T006 Create DeserializationException custom exception class
  - File: `runtime/src/main/java/io/nats/ext/subscriber/DeserializationException.java`
  - Content: Extend RuntimeException, used for JSON/type deserialization failures
  - Constructor overloads: `(String message)`, `(String message, Throwable cause)`

- [ ] T007 Implement MessageDeserializer utility with native type decoding strategies
  - File: `runtime/src/main/java/io/nats/ext/subscriber/MessageDeserializer.java`
  - Method signature (overload 1): `public static <T> T deserialize(byte[] data, Class<T> targetType, ObjectMapper mapper) throws DeserializationException`
  - Method signature (overload 2): `public static <T> T deserialize(byte[] data, com.fasterxml.jackson.core.type.TypeReference<T> typeRef, ObjectMapper mapper) throws DeserializationException`
  - Decoder strategy (mirrors TypedPayloadEncoder):
    - **Native String types** (String, primitive wrappers like Integer, Long): Direct UTF-8 decode + parse
    - **Byte arrays** (byte[], Byte): Base64 decode
    - **Primitive arrays** (int[], long[], double[], etc.): Split space-separated string, parse each element
    - **String arrays** (String[]): Split comma-separated string
    - **Complex types** (POJOs, records, generics): Jackson ObjectMapper.readValue()
  - Error handling: Catch all exceptions, wrap as DeserializationException with context
  - Null check: Throw DeserializationException if deserialized object is null

- [ ] T008 Write unit tests for CloudEventUnwrapper
  - File: `runtime/src/test/java/io/nats/ext/subscriber/CloudEventUnwrapperTest.java`
  - Test cases:
    1. Valid binary-mode CloudEvent with all required headers → returns payload bytes
    2. Missing `ce-specversion` header → throws CloudEventException
    3. Missing `ce-type` header → throws CloudEventException
    4. Missing `ce-source` header → throws CloudEventException
    5. Missing `ce-id` header → throws CloudEventException
    6. `ce-specversion != "1.0"` → throws CloudEventException
    7. Multiple messages with different payloads → correct payload extraction for each
  - Note: Structured-mode CloudEvents (with CloudEvent envelope in payload) automatically fail header validation tests above, so no separate test needed
  - Use Mockito to mock NATS Message or create test helper
  - Assertions: AssertJ fluent assertions required

- [ ] T009 Write unit tests for MessageDeserializer - Native types
  - File: `runtime/src/test/java/io/nats/ext/subscriber/MessageDeserializerTest.java` (part 1)
  - Test cases:
    1. Integer parameter: byte[] "42" → Integer(42)
    2. Long parameter: byte[] "9223372036854775807" → Long max value
    3. Boolean parameter: byte[] "true" → Boolean.TRUE
    4. String parameter: byte[] "hello" → String("hello")
    5. byte[] parameter: byte[] base64("AQID") → byte[] {1, 2, 3}
    6. int[] parameter: byte[] "1 2 3" → int[] {1, 2, 3}
    7. long[] parameter: byte[] "100 200 300" → long[] {100, 200, 300}
    8. String[] parameter: byte[] "a,b,c" → String[] {"a", "b", "c"}
    9. Invalid encoding (e.g., "abc" → Integer) → throws DeserializationException
  - Assertions: AssertJ fluent assertions required

---

## Phase 3: User Story 1 - Receive Typed Messages via Annotated Methods (P1)

**User Story Goal**: Developers can annotate methods with `@NatsSubscriber` and receive automatically deserialized typed objects (POJOs, records, generics) instead of raw strings.

**Independent Test Criteria**:
- Define a Quarkus bean with @NatsSubscriber method expecting a POJO type
- Publish a JSON message to that subject (via publisher or manual publish)
- Verify the method receives the correctly deserialized object instance with all fields populated

**Acceptance Scenarios**:
1. SC-001: CloudEvents message with User object data is received, unwrapped, and deserialized into User parameter
2. SC-002: Multiple typed parameters (POJO, records, generics) are supported; each method has exactly one parameter receiving deserialized CloudEvents data

- [ ] T010 Write unit tests for MessageDeserializer - Complex types
  - File: `runtime/src/test/java/io/nats/ext/subscriber/MessageDeserializerTest.java` (part 2)
  - Test cases:
    1. POJO parameter (User class): JSON → User instance with all fields populated
    2. Record parameter (Java 21): Record from JSON string
    3. Generic type parameter (List<User>): JSON array → List<User> with TypeReference
    4. Nested POJO (Order containing OrderItem list): JSON with nested objects → Order instance
    5. Custom ObjectMapper: Inject custom mapper, verify it's used for deserialization
    6. Deserialization failure (JSON doesn't match type): throws DeserializationException
    7. Missing required field: throws DeserializationException
    8. Null result from deserialization: throws DeserializationException
  - Create test POJOs/records (User, Order, OrderItem) in test package
  - Assertions: AssertJ fluent assertions required

- [ ] T011 Enhance SubscriberProcessor to validate parameter types are supported (matches T007 decoder strategy)
  - File: `deployment/src/main/java/io/nats/ext/deployment/processor/SubscriberProcessor.java` (modify)
  - Action: Enhance existing 004 processor to add type validation
  - Validation steps:
    1. Check method has exactly 1 parameter (inherited from 004)
    2. NEW: Check parameter type is supported by MessageDeserializer decoder strategy:
       - ✅ Primitive Wrappers: Integer, Long, Double, Float, Boolean, Short, Character
       - ✅ String (UTF-8)
       - ✅ Byte types: byte[], Byte
       - ✅ Primitive arrays: int[], long[], double[], float[], boolean[], short[], char[]
       - ✅ String arrays: String[]
       - ✅ Complex types: Jackson-deserializable POJOs, records, generics
       - ❌ Raw primitives: int, long, boolean, etc. (use wrapper types)
       - ❌ Interfaces without implementation
       - ❌ Abstract classes without @JsonDeserialize
    3. For complex types: Introspect using Jackson TypeFactory to ensure it's deserializable
    4. If type check fails: Generate BUILD ERROR with clear message including method name and type
  - Build error messages:
    - "Method foo(int) parameter int is a raw primitive; use wrapper type Integer instead"
    - "Method foo(MyInterface) parameter MyInterface is not Jackson-deserializable; ensure it has a no-arg constructor or @JsonCreator annotation"

- [ ] T012 [P] Write integration tests for TypedSubscriber - POJO reception
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberIT.java` (part 1)
  - Test case: SC-001 acceptance test
    1. Start NATS server (via Testcontainers)
    2. Deploy Quarkus app with subscriber bean containing @NatsSubscriber(subject="orders") expecting Order POJO
    3. Publish CloudEvents-formatted Order JSON to "orders" subject
    4. Wait (with Awaitility) for subscriber method to execute
    5. Assert: Order object was received with correct fields (orderId, customerId, etc. all populated)
  - Create test Order POJO in integration-tests
  - Use Awaitility for async message arrival (no Thread.sleep)
  - Use NatsPublisher or manual publish with correct binary-mode CloudEvents format

- [ ] T013 [P] Write integration tests for TypedSubscriber - Record reception
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberIT.java` (part 2)
  - Test case: SC-002 variant with Java 21 records
    1. Deploy Quarkus app with subscriber expecting User record (Java 21)
    2. Publish CloudEvents JSON message with User data
    3. Wait for subscriber method execution
    4. Assert: User record was received with all fields correctly deserialized
  - Create test User record in integration-tests
  - Identical structure to T012 but with record type

- [ ] T014 [P] Write integration tests for TypedSubscriber - Generic type reception
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberIT.java` (part 3)
  - Test case: SC-002 variant with List<T>
    1. Deploy app with subscriber expecting List<Item> generic parameter
    2. Publish CloudEvents JSON array to subject
    3. Wait for subscriber method execution
    4. Assert: List<Item> was received with correct number of items and all fields populated
  - Create test Item class/record
  - Verify TypeReference handling for generic types

- [ ] T015 Configure integration-tests module to use Testcontainers NATS
  - File: `integration-tests/pom.xml`
  - Add testcontainers-nats dependency (if not present)
  - Create test `application.properties` for integration tests pointing to NATS container
  - Verify -Pit profile activates integration tests

---

## Phase 4: User Story 2 - Unwrap and Deserialize CloudEvents Data (P1)

**User Story Goal**: Subscriber methods automatically unwrap CloudEvents-wrapped messages (per CloudEvents 1.0 specification) and deserialize the event data into typed objects.

**Independent Test Criteria**:
- Publish a CloudEvents-formatted message with a User object as the event data
- Verify that a subscriber method receives the User object deserialized from the CloudEvent's data field (not the CloudEvent wrapper)

**Acceptance Scenarios**:
1. SC-003: CloudEvents message with User data is received, unwrapped, and data is correctly deserialized to User type
2. SC-004: Non-CloudEvents messages are nacked and logged appropriately
3. SC-005: Deserialization failures (JSON doesn't match type) are nacked and logged with error details

- [ ] T016 [P] Implement SubscriberMessageHandler enhancement to call CloudEventUnwrapper
  - File: `runtime/src/main/java/io/nats/ext/subscriber/SubscriberMessageHandler.java` (modify from 004 or create if not present)
  - Action: Modify message processing loop to add CloudEvent unwrapping step:
    ```
    Message msg = nextMessage()
    // NEW: Step 1 - Unwrap CloudEvent
    byte[] eventData = CloudEventUnwrapper.unwrapData(msg)

    // Step 2 - Deserialize to typed object (via MessageDeserializer)
    Object typedPayload = MessageDeserializer.deserialize(eventData, parameterType, objectMapper)

    // Step 3 - Invoke method
    method.invoke(instance, typedPayload)

    // Step 4 - Ack/nak (inherited from 004)
    ```
  - Error handling: CloudEventException and DeserializationException both trigger nak + error log (inherited from 004)
  - Inject ObjectMapper via CDI constructor injection (per CLAUDE.md)

- [ ] T017 [P] Write integration tests for CloudEvent unwrapping - Binary-mode messages
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberCloudEventsIT.java` (part 1)
  - Test case: SC-003 acceptance test
    1. Publish binary-mode CloudEvents message (attributes in headers ce-*, data in payload)
    2. Verify subscriber receives correctly unwrapped and deserialized object
    3. Assert: Method invoked with typed object (not CloudEvent wrapper)
  - Publish using NatsPublisher (which sends binary-mode per 005) or manual binary-mode publish
  - Use Awaitility to wait for message processing

- [ ] T018 [P] Write integration tests for CloudEvent unwrapping - Invalid messages rejection
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberCloudEventsIT.java` (part 2)
  - Test cases:
    1. SC-004a: Missing `ce-specversion` header → message nacked, error logged, next message processed
    2. SC-004b: Missing `ce-type` header → message nacked, error logged, next message processed
    3. SC-004c: `ce-specversion != "1.0"` → message nacked, error logged
    4. SC-004d: Raw JSON (not CloudEvents) → message nacked, error logged
    5. SC-004e: Structured-mode CloudEvent in payload → message nacked, error logged
  - Publish invalid messages, verify they are nacked (not redelivered immediately)
  - Publish valid message after invalid → verify valid message is processed (continues after error)
  - Capture logs and verify error-level messages include subject and method name (per FR-006)

- [ ] T019 [P] Write integration tests for deserialization failure handling
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberCloudEventsIT.java` (part 3)
  - Test cases:
    1. SC-005a: CloudEvents with JSON that doesn't match target type → nacked, error logged with subject and method name
    2. SC-005b: CloudEvents with missing required JSON field → nacked, error logged
    3. SC-005c: CloudEvents with malformed JSON → nacked, error logged
    4. Invalid then valid sequence → verify recovery (valid message processed after invalid one nacked)
  - Log capture: Verify error logs include subject, method name, exception details (per FR-006)
  - Do NOT log message payload (per FR-006 security requirement)

- [ ] T020 Implement error logging in SubscriberMessageHandler for CloudEvents and deserialization failures
  - File: `runtime/src/main/java/io/nats/ext/subscriber/SubscriberMessageHandler.java` (modify)
  - Logging on CloudEventException: Log at ERROR level: "CloudEvent validation failed for subject={subject}, method={methodName}, cause={exceptionMessage}"
  - Logging on DeserializationException: Log at ERROR level: "Message deserialization failed for subject={subject}, method={methodName}, type={targetType}, cause={exceptionMessage}"
  - Do NOT log message payload or event data for security
  - Use SLF4J logger (injected via constructor)

- [ ] T021 Write test to verify custom ObjectMapper injection (FR-007)
  - File: `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberObjectMapperIT.java` (new)
  - Test case:
    1. Create custom ObjectMapper bean with special configuration (e.g., custom deserializer)
    2. Deploy app with subscriber using custom ObjectMapper
    3. Publish message that requires custom deserializer to parse correctly
    4. Verify subscriber uses the custom ObjectMapper (message is deserialized correctly)
  - Alternative: Verify default Quarkus ObjectMapper is used if no custom bean provided

---

## Phase 5: User Story 3 - Auto-Inject Subscriber Bean into Application (P2)

**User Story Goal**: Developers don't need to manually manage subscriber lifecycle. The Quarkus extension automatically discovers, registers, and injects subscriber beans.

**Independent Test Criteria**:
- Define a Quarkus bean with @NatsSubscriber methods
- Inject it into another bean or test
- Verify the bean is properly registered and receives messages

**Acceptance Scenarios**:
1. SC-006a: Subscriber bean is automatically instantiated when application starts
2. SC-006b: Subscriber bean can be injected into other beans via @Inject (if needed for application logic)

- [ ] T022 Write integration test for subscriber bean lifecycle and auto-registration
  - File: `integration-tests/src/test/java/io/nats/ext/it/SubscriberBeanLifecycleIT.java` (new)
  - Test case: SC-006a acceptance test
    1. Deploy Quarkus app with subscriber bean marked as @ApplicationScoped
    2. Verify on startup: subscriber bean is instantiated and connected to NATS subjects
    3. Publish message to subject
    4. Verify subscriber method receives message (implies bean is running)
  - Use `@Inject` to verify bean is discoverable if needed by other components

- [ ] T023 Write integration test for subscriber bean injection into other beans
  - File: `integration-tests/src/test/java/io/nats/ext/it/SubscriberBeanInjectionIT.java` (new)
  - Test case: SC-006b acceptance test
    1. Create subscriber bean with public method or state to track message reception
    2. Create another bean that injects the subscriber bean
    3. Verify injection succeeds
    4. Publish message, verify both beans receive notification (if applicable) or subscriber methods are invoked
  - Note: Per the project constitution, constructor injection is preferred over field injection.

---

## Phase 6: Polish & Cross-Cutting Concerns

Final validation, documentation, and error handling verification.

- [ ] T024 Verify build-time validation error messages are clear and actionable
  - File: `deployment/src/test/java/io/nats/ext/deployment/processor/SubscriberProcessorTest.java` (create or enhance)
  - Test cases:
    1. Method with raw primitive parameter (int, long, boolean) → build fails with message "is a raw primitive; use wrapper type Integer instead"
    2. Method with unsupported type (interface without implementation) → build fails with message "Parameter Type is not Jackson-deserializable; ensure it has a no-arg constructor or @JsonCreator"
    3. Method with valid type (Integer, String, POJO, record) → build succeeds
    4. Method with valid generic type (List<User>) → build succeeds
    5. Method with 0 parameters → build fails (inherited from 004)
    6. Method with 2+ parameters → build fails (inherited from 004)
  - Verify error messages are informative and suggest solutions

- [ ] T025 Create example subscriber application in integration-tests demonstrating all supported types
  - File: `integration-tests/src/test/java/io/nats/ext/it/example/ExampleSubscriberApp.java` (create)
  - Include subscriber methods for:
    - POJO type (Order)
    - Record type (User)
    - Generic type (List<Item>)
    - Primitive wrapper (Integer)
    - String
    - Byte array
    - Primitive array (int[])
    - String array
  - Each method logs when invoked (for manual testing)
  - File: Update `integration-tests/README.md` or create documentation with usage examples

- [ ] T026 Verify implicit ack/nak mechanism inherited from 004 still works with typed deserialization
  - File: Integration test verification
  - Test case:
    1. Subscriber method processes message successfully → implicit ack
    2. Subscriber method throws exception → implicit nak (message redelivered)
    3. CloudEventUnwrapper throws exception → implicit nak (message redelivered)
    4. MessageDeserializer throws exception → implicit nak (message redelivered)
  - Verify message acknowledgment status changes after each scenario
  - Use NATS consumer metrics or message redelivery observation

- [ ] T027 Add Awaitility dependency to integration-tests pom.xml
  - File: `integration-tests/pom.xml`
  - Add: `<dependency><groupId>org.awaitility</groupId><artifactId>awaitility</artifactId><version>4.1.1</version><scope>test</scope></dependency>`
  - Verify in all async test cases (T012-T014, T017-T019, T022-T023)

- [ ] T028 Run full test suite: unit + integration tests, verify coverage >80% for new code
  - Command: `./mvnw clean install -Pit`
  - Verify:
    1. All unit tests pass (CloudEventUnwrapperTest, MessageDeserializerTest, SubscriberProcessorTest)
    2. All integration tests pass (TypedSubscriberIT, TypedSubscriberCloudEventsIT, SubscriberBeanLifecycleIT, etc.)
    3. Code coverage >80% for new classes (CloudEventUnwrapper, MessageDeserializer, SubscriberMessageHandler changes, SubscriberProcessor changes)
    4. Build succeeds without errors or warnings
  - Coverage tools: Jacoco (if configured in pom.xml)

---

## Dependencies & Execution Order

### Phase Completion Order

1. **Phase 1** (Setup) → Phase 2 (Foundation)
2. **Phase 2** (Foundation) → Phase 3 & 4 (User Stories can be parallel)
3. **Phase 3** & **Phase 4** → Phase 5 (Auto-injection)
4. **Phase 5** → Phase 6 (Polish)

### Within-Phase Parallelization

**Phase 3**: T012, T013, T014 can run in parallel (each creates separate integration test class)
**Phase 4**: T017, T018, T019 can run in parallel (different test variants, no shared state)
**Phase 5**: T022, T023 can run in parallel (independent beans, separate test files)

### Blocking Dependencies

- T005 (CloudEventUnwrapper) must complete before T016 (integration)
- T007 (MessageDeserializer) must complete before T016 (integration)
- T011 (SubscriberProcessor validation) must complete before T024 (error message tests)
- T004, T006 (custom exceptions) must complete before T005, T007 (used in those classes)

---

## Recommended MVP Execution Path

For a minimal viable product delivery:

### Phase 1: Setup (all 3 tasks required)
- T001, T002, T003

### Phase 2: Foundation (all 5 tasks required)
- T004, T005, T006, T007, T008, T009

### Phase 3: User Story 1 (core MVP - 5 tasks minimum)
- T010 (unit tests - optional but recommended)
- T011 (build-time validation - critical)
- T012, T013 (integration tests - at least one POJO + Record test)

**Result**: Developers can annotate methods with @NatsSubscriber and receive typed POJO/record objects.

### Extended MVP (add Phase 4 - 3 tasks minimum)
- T016 (unwrapping integration)
- T017 (binary-mode test - critical)
- T018 (invalid message rejection - critical)

**Result**: Full CloudEvents support with error handling for malformed messages.

---

## Success Metrics

| Metric | Criterion | Verification |
|--------|-----------|--------------|
| SC-001 | Typed POJO reception | T012 integration test passes |
| SC-002 | Multiple type support | T012, T013, T014 all pass |
| SC-003 | CloudEvents unwrapping | T017 integration test passes |
| SC-004 | Non-CloudEvents rejection | T018 integration test passes |
| SC-005 | Deserialization error handling | T019 integration test passes |
| SC-006 | Build-time type validation | T024 build error tests pass |
| SC-007 | Implicit ack/nak | T026 verification test passes |
| Coverage | >80% new code | T028 jacoco report |

---

## Files Modified/Created Summary

### New Files
- `runtime/src/main/java/io/nats/ext/subscriber/CloudEventUnwrapper.java`
- `runtime/src/main/java/io/nats/ext/subscriber/CloudEventException.java`
- `runtime/src/main/java/io/nats/ext/subscriber/MessageDeserializer.java`
- `runtime/src/main/java/io/nats/ext/subscriber/DeserializationException.java`
- `runtime/src/test/java/io/nats/ext/subscriber/CloudEventUnwrapperTest.java`
- `runtime/src/test/java/io/nats/ext/subscriber/MessageDeserializerTest.java`
- `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberIT.java`
- `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberCloudEventsIT.java`
- `integration-tests/src/test/java/io/nats/ext/it/TypedSubscriberObjectMapperIT.java`
- `integration-tests/src/test/java/io/nats/ext/it/SubscriberBeanLifecycleIT.java`
- `integration-tests/src/test/java/io/nats/ext/it/SubscriberBeanInjectionIT.java`
- `integration-tests/src/test/java/io/nats/ext/it/example/ExampleSubscriberApp.java`

### Modified Files
- `runtime/pom.xml` (verify dependencies)
- `runtime/src/main/java/io/nats/ext/subscriber/SubscriberMessageHandler.java` (add unwrapping + logging)
- `deployment/src/main/java/io/nats/ext/deployment/processor/SubscriberProcessor.java` (add type validation)
- `deployment/src/test/java/io/nats/ext/deployment/processor/SubscriberProcessorTest.java` (add validation tests)
- `integration-tests/pom.xml` (add Testcontainers, Awaitility)

---

**Status**: Ready for implementation via `/speckit.implement` command

