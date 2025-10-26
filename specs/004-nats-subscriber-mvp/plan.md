# Implementation Plan: @NatsSubscriber Annotation (MVP)

**Branch**: `004-nats-subscriber-mvp` | **Date**: 2025-10-26 | **Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-nats-subscriber-mvp/spec.md`

## Summary

This feature introduces a `@NatsSubscriber` annotation to the Quarkus EasyNats extension. It will allow developers to create ephemeral consumers for NATS subjects by annotating a method. The initial MVP will focus on consuming simple `String` payloads.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Quarkus 3.27.0, NATS JetStream client (io.nats:jnats)
**Storage**: N/A
**Testing**: JUnit 5, AssertJ, Quarkus Test Framework
**Target Platform**: Quarkus applications (JVM and native)
**Project Type**: Quarkus Extension (multi-module Maven project)
**Performance Goals**: Minimal impact on application startup time.
**Constraints**: Must integrate with Quarkus CDI and GraalVM native compilation.
**Scale/Scope**: This feature is scoped to the `@NatsSubscriber` annotation for `String` payloads and ephemeral consumers.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Extension-First Architecture | ✅ PASS | The implementation will follow the existing multi-module structure. |
| II. Minimal Runtime Dependencies | ✅ PASS | No new runtime dependencies are required. |
| III. Test-Driven Development | ✅ PASS | New unit and integration tests will be added. |
| IV. Java 21 Compatibility | ✅ PASS | All new code will be Java 21 compatible. |
| V. CloudEvents Compliance | ✅ PASS | This feature is orthogonal to CloudEvents. |
| VI. Developer Experience First | ✅ PASS | The annotation-driven approach enhances developer experience. |
| VII. Observability First | ✅ PASS | This feature is orthogonal to observability. |

## Project Structure

### Documentation (this feature)

```text
specs/004-nats-subscriber-mvp/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── annotation-contract.md
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
runtime/
└── src/main/java/org/mjelle/quarkus/easynats/
    └── NatsSubscriber.java         # The new annotation

deployment/
└── src/main/java/org/mjelle/quarkus/easynats/deployment/
    └── QuarkusEasyNatsProcessor.java # Build step for discovering subscribers

integration-tests/
└── src/test/java/org/mjelle/quarkus/easynats/it/
    └── NatsSubscriberTest.java     # New integration test
```

**Structure Decision**: The existing multi-module Maven structure for Quarkus extensions will be used.

## Complexity Tracking

No violations to the constitution were identified.

## Implementation Strategy

For each method annotated with `@NatsSubscriber`, the extension will implement the following strategy:

1.  **Ephemeral Consumer Creation**: An ephemeral consumer will be created on the NATS JetStream by creating a consumer configuration without a name. This allows the NATS server to assign a unique, random name, ensuring the consumer is truly ephemeral and does not conflict with other instances.
2.  **ConsumerContext API**: The implementation will use the modern JetStream `ConsumerContext` API to interact with the consumer.
3.  **Message Handler**: A message handler will be registered using `consumerContext.consume(handler)`. This handler will contain the logic to:
    a.  Receive the `Message` from the NATS server.
    b.  Extract the `String` payload.
    c.  Invoke the developer's annotated method with the payload.
    d.  Handle implicit acknowledgment (`ack`/`nak`) based on whether the method execution was successful or threw an exception.