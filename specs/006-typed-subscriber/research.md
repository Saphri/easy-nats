# Research Artifacts: Typed Subscriber with @NatsSubscriber Annotation

**Phase**: 0 - Research & Clarification
**Date**: 2025-10-27
**Status**: Complete (no clarifications needed)

## Technical Decisions

### Decision 1: CloudEvents Binary-Mode Unwrapping Strategy

**What was chosen**: Support **binary-mode CloudEvents only** (attributes in NATS headers with `ce-` prefix, data in message payload). Extract event data from payload and deserialize into typed parameter.

**Rationale**:
- CloudEvents 1.0 spec defines two content modes: binary-mode and structured-mode
  - **Binary-mode** (chosen): Attributes in message headers (ce-specversion, ce-type, ce-source, ce-id), data in payload
  - **Structured-mode** (rejected): Entire CloudEvent envelope in payload (JSON/Avro)
- All messages in this system use binary-mode per Principle V and existing NatsPublisher.java implementation
- Binary-mode: Headers-based attribute storage (ce-* prefix) keeps payload clean for event data
- Unwrapping before deserialization simplifies type safety
- Reference: https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md

**Implementation approach**:
- CloudEventUnwrapper utility validates CloudEvents 1.0 binary-mode headers (ce-specversion, ce-type, ce-source, ce-id)
- Validates binary-mode format (not structured-mode)
- Extracts event data bytes from NATS message payload
- Passes extracted data to MessageDeserializer for type conversion

**Alternatives considered**:
1. **Support both binary-mode and structured-mode**: Rejected - adds complexity, existing NatsPublisher only supports binary-mode, no business case for structured-mode in this extension
2. **Pass full CloudEvent object to method**: Rejected - complicates type system, requires CloudEvents SDK on classpath, violates simplicity principle
3. **Support both CloudEvents and raw JSON**: Rejected - contradicts spec requirement that ALL messages are CloudEvent-wrapped in binary-mode
4. **Custom unwrapping without CloudEvents SDK**: Rejected - CloudEvents SDK is already available (used in 005), no benefit to reimplementing; header parsing is straightforward

---

### Decision 2: Jackson ObjectMapper Configuration

**What was chosen**: Use default Jackson ObjectMapper provided by Quarkus. Support optional custom configuration via Quarkus producer beans.

**Rationale**:
- Quarkus provides managed ObjectMapper beans by default (Jackson auto-configuration)
- Developers can inject custom ObjectMapper if needed (standard Quarkus pattern)
- Reduces runtime complexity (no custom ObjectMapper factory)
- Aligns with Principle VI (configuration-less DX for common case)

**Implementation approach**:
- Inject ObjectMapper via CDI into MessageDeserializer
- MessageDeserializer uses ObjectMapper.readValue(jsonString, targetClass)
- Support generic types via TypeReference (e.g., List<User>)
- Developers can override ObjectMapper configuration in their application

**Alternatives considered**:
1. **Create custom ObjectMapper factory in extension**: Rejected - Quarkus already provides, adds unnecessary complexity
2. **Hardcode ObjectMapper settings**: Rejected - prevents customization; Quarkus pattern is to use producers
3. **Support GSON/Moshi as alternative**: Rejected - adds complexity, Jackson is standard in Quarkus

---

### Decision 3: Build-Time Type Validation

**What was chosen**: Validate parameter types at build time (in SubscriberProcessor). Check that types are Jackson-deserializable.

**Rationale**:
- Fail-fast: catch configuration errors before deployment
- Reduces runtime overhead (no reflection checks during message processing)
- Aligns with Principle III (TDD + validation at boundaries)
- Consistent with 004-nats-subscriber-mvp validation approach

**Implementation approach**:
- SubscriberProcessor (deployment module) inspects method parameter type
- For each method annotated with @NatsSubscriber:
  - Validate parameter count = 1 (inherited from 004)
  - Check parameter type is not String (String-only is 004 feature)
  - Attempt to introspect type using Jackson TypeFactory
  - Fail build if type cannot be deserialized (e.g., interface without impl, primitive wrapper)
- Build failure message includes method name and type that failed

**Alternatives considered**:
1. **Runtime type checking**: Rejected - adds latency to message processing, violates performance goals
2. **Support any type and fail at first message**: Rejected - poor DX, violations discovered late
3. **Restrict to known types (POJO, records)**: Rejected - unnecessarily limiting, Jackson supports generics

---

### Decision 4: Non-CloudEvents Message Rejection

**What was chosen**: Nack message if it's not a valid CloudEvent 1.0.

**Rationale**:
- Spec requirement: ALL messages MUST be CloudEvent-wrapped
- Nacking allows redelivery (message may be resent correctly formatted)
- Logging error enables operator debugging
- Prevents silent message loss

**Implementation approach**:
- CloudEventUnwrapper validates required headers (ce-specversion, ce-type, ce-source, ce-id)
- If any required header missing: throw exception
- Exception caught by inherited 004 exception handler
- Message is nacked and error logged at ERROR level
- Application continues processing other messages

**Alternatives considered**:
1. **Drop message silently**: Rejected - violates observability, message loss not detected
2. **Auto-convert raw JSON to minimal CloudEvent**: Rejected - violates spec requirement
3. **Acknowledge and skip**: Rejected - preferred over nack, but violates intent (retry with fix)

---

### Decision 5: Error Handling for Deserialization Failures

**What was chosen**: Nack message on deserialization failure, log error at ERROR level (inherited from 004).

**Rationale**:
- Malformed JSON or type mismatch may be transient (message was correct when sent, corruption occurred)
- Nacking allows redelivery with potential fix
- Error logging enables debugging
- Consistent with 004 exception handling model

**Implementation approach**:
- MessageDeserializer.deserialize() throws exception if ObjectMapper fails
- Exception bubbles to message handler loop (inherited from 004)
- 004's handler catches exception, nacks message, logs at ERROR level
- No changes needed to error handling (inheritance from 004 is sufficient)

**Alternatives considered**:
1. **Dead-letter queue (DLQ)**: Rejected - out of scope for this MVP, deferred to future
2. **Skip and continue**: Rejected - message loss not observable
3. **Acknowledge and discard**: Rejected - violates recovery principle

---

## Design Patterns

### Pattern 1: Two-Stage Decoding (Mirror of TypedPayloadEncoder)

1. **CloudEventUnwrapper**: Parses NATS message headers → validates CloudEvents 1.0 binary-mode → extracts data payload bytes
2. **MessageDeserializer**: Receives extracted data bytes → determines decoder strategy (matches TypedPayloadEncoder) → returns typed object

**Decoder Strategy** (mirrors TypedPayloadEncoder.resolveEncoder()):
- **Native decoding**: Primitive wrappers, String, byte arrays, primitive arrays, String arrays → Direct parsing/base64/space-sep/comma-sep
- **Complex decoding**: POJOs, records, generics → Jackson JSON deserialization

**Benefit**:
- Separation of concerns between CloudEvent handling and data decoding
- Symmetry with publisher: whatever NatsPublisher can send, subscriber can receive
- Testable independently
- Clear error sources

---

### Pattern 2: Type Erasure Handling

Generic types (e.g., `List<User>`) face Java type erasure. Method reflection cannot distinguish List<User> from List<String> at runtime.

**Solution**: Use Jackson TypeReference to preserve type information at compile time.

Example:
```java
// At runtime, method parameter is just List<?>
// But @Nats Subscriber processing records the generic type at compile-time
// SubscriberProcessor builds TypeReference from resolved method signature
ObjectMapper mapper = ...;
List<User> users = mapper.readValue(json, new TypeReference<List<User>>() {});
```

---

## Testing Strategy

### Unit Tests (runtime/src/test)

1. **CloudEventUnwrapperTest**: Valid/invalid CloudEvent headers, data extraction, error cases
2. **MessageDeserializerTest**: JSON → POJO, records, generics, error cases, custom ObjectMapper

### Integration Tests (integration-tests)

1. **TypedSubscriberIT**: End-to-end with real NATS server, publish CloudEvent, verify typed object reception
2. Variants: POJO, record, List<T>, error scenarios

### Coverage Target

≥80% for new code (CloudEventUnwrapper, MessageDeserializer, SubscriberProcessor changes).

---

## Open Questions (None)

All technical decisions resolved. No clarifications blocking implementation.
