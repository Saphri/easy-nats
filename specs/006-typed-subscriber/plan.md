# Implementation Plan: Typed Subscriber with @NatsSubscriber Annotation

**Branch**: `006-typed-subscriber` | **Date**: 2025-10-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/006-typed-subscriber/spec.md`

**Note**: This plan builds on top of `004-nats-subscriber-mvp` which provides basic subscriber infrastructure.

## Summary

Extend the basic `@NatsSubscriber` annotation (from 004) with CloudEvents 1.0 unwrapping and typed message deserialization. Replace String-only payload handling with full support for POJOs, records, and generic types. All messages must be CloudEvents-wrapped; unwrap the event data field and deserialize using Jackson ObjectMapper into the method's parameter type. Implicit ack/nak mechanism (inherited from 004) continues to work: successful execution acknowledges, exceptions cause nack with error logging.

## Technical Context

**Language/Version**: Java 21 (enforced per Constitution Principle IV)
**Primary Dependencies**:
- Quarkus 3.27.0 (core framework)
- JNats (NATS Java client with JetStream support)
- Jackson Databind (JSON deserialization)
- CloudEvents SDK for Java 1.0 (CloudEvents standard implementation)

**Storage**: N/A (messaging system, no data storage)

**Testing**:
- JUnit 5 + Awaitility (async testing without Thread.sleep)
- AssertJ (fluent assertions - per Constitution CLAUDE.md)
- Testcontainers with NATS container (integration tests)
- `-Pit` profile activates integration tests

**Target Platform**: JVM (Quarkus application running on Linux/cloud)

**Project Type**: Multi-module Maven extension (runtime/deployment/integration-tests)

**Performance Goals**:
- Message deserialization latency: <10ms per message (typical for Jackson)
- CloudEvents unwrapping: minimal overhead (<1ms per message)
- No async message processing (push-based; JNats handles concurrency)

**Constraints**:
- Runtime module size: <500 KB (per Constitution Principle II)
- Zero new dependencies in runtime module (only Jackson which is already used in 005-transparent-cloudevents)
- Build-time validation only (no runtime type checking overhead)
- Fail-fast at startup if types aren't Jackson-deserializable

**Scale/Scope**:
- Extension supports unlimited subscribers per application
- Each subscriber has one ephemeral consumer (from 004-nats-subscriber-mvp)
- Message throughput determined by JNats client (typically 10k+ msg/sec)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

✅ **I. Extension-First Architecture**
- Feature extends `@NatsSubscriber` from 004 (part of deployment processor)
- Deserialization logic in runtime module
- Build-time type validation in deployment processor
- Uses Quarkus @BuildStep pattern (inherited from 004)
- No violation of runtime/deployment separation

✅ **II. Minimal Runtime Dependencies**
- Jackson Databind: Already used in 005-transparent-cloudevents, no new dependency
- CloudEvents SDK: Needed to parse CloudEvent headers per Principle V (no new dependency if CloudEvents already present)
- JNats: Already required by 004
- No additional dependencies added
- No Spring/configuration frameworks

✅ **III. Test-Driven Development**
- Tests required: deserialization, CloudEvents unwrapping, type validation, error handling
- Coverage target: >80% (inherited requirement from constitution)
- Integration tests validate with real NATS/CloudEvents messages

✅ **IV. Java 21 Compatibility**
- Records may be used for test data POJOs
- Pattern matching for CloudEvent validation
- No <Java 21 syntax

✅ **V. CloudEvents Compliance**
- Mandatory CloudEvents 1.0 **binary-mode** unwrapping (aligns with 005-transparent-cloudevents)
- Binary-mode: Attributes in NATS message headers with `ce-` prefix, data in payload
- Structured-mode (entire CloudEvent in payload) is NOT supported
- Extracts event data from message payload (not from CloudEvent envelope in headers)
- Non-CloudEvents and non-binary-mode messages are rejected (enforces compliance)
- Existing NatsPublisher.java already publishes in binary-mode only

✅ **VI. Developer Experience First**
- `@NatsSubscriber` remains simple: single parameter of typed object
- Implicit ack/nak inherited from 004
- Jackson deserialization is transparent to developer
- Build-time validation catches type errors early

✅ **VII. Observability First**
- Inheritance: error logging from 004 applies to deserialization failures
- No observability infrastructure changes needed

**Status**: ✅ ALL GATES PASS - No violations of constitution principles

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

This feature extends the existing Quarkus extension multi-module structure (no new modules created).

```text
runtime/
├── src/main/java/io/nats/ext/
│   └── subscriber/
│       ├── CloudEventUnwrapper.java        # NEW: Extract data field from CloudEvent
│       ├── MessageDeserializer.java        # NEW: Deserialize data into typed objects
│       └── SubscriberMessageHandler.java   # MODIFIED: Add deserialization step
│
├── src/test/java/io/nats/ext/subscriber/
│   ├── CloudEventUnwrapperTest.java        # NEW: Unit tests for unwrapping logic
│   ├── MessageDeserializerTest.java        # NEW: Unit tests for deserialization
│   └── SubscriberIntegrationTest.java      # NEW: Integration tests with NATS

deployment/
├── src/main/java/io/nats/ext/deployment/
│   └── subscriber/
│       └── SubscriberProcessor.java        # MODIFIED: Add build-time type validation
│
└── src/test/java/io/nats/ext/deployment/
    └── SubscriberProcessorTest.java        # MODIFIED: Add type validation tests

integration-tests/
├── src/test/java/io/nats/ext/it/
│   └── TypedSubscriberIT.java              # NEW: Full integration tests
│
└── src/test/resources/
    └── application.properties               # MODIFIED: NATS config for tests
```

**Structure Decision**: Multi-module Maven extension (inherited from 004).
- **runtime**: Deserialization logic (CloudEventUnwrapper, MessageDeserializer)
- **deployment**: Build-time type validation (enhanced SubscriberProcessor)
- **integration-tests**: End-to-end tests with real NATS/CloudEvents messages

## Complexity Tracking

✅ **No Constitution violations** - No complexity tracking needed.

---

## Phase 0 & Phase 1 Completion

✅ **research.md**: All NEEDS CLARIFICATION items resolved (5 design decisions documented)
✅ **data-model.md**: Complete data flow, entities, validation rules, state transitions
✅ **quickstart.md**: Usage examples, error handling, troubleshooting, migration guide
✅ **Plan.md**: This file - technical context, constitution check, project structure

**Ready for Phase 2**: `/speckit.tasks` command can now generate implementation tasks
