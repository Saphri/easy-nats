# Implementation Plan: @NatsSubject Annotation

**Branch**: `003-nats-subject` | **Date**: 2025-10-26 | **Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-nats-subject/spec.md`

## Summary

This feature introduces a `@NatsSubject` annotation to the Quarkus EasyNats extension. It allows developers to inject a `NatsPublisher` instance with a pre-configured NATS subject, decoupling the subject from application logic. The implementation will use a CDI `@Produces` method and `InjectionPoint` to create configured `NatsPublisher` instances at runtime.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Quarkus 3.27.0, NATS JetStream client
**Storage**: N/A
**Testing**: JUnit 5, Mockito, AssertJ, Quarkus Test Framework
**Target Platform**: Quarkus applications (JVM and native)
**Project Type**: Quarkus Extension (multi-module Maven project)
**Performance Goals**: Minimal impact on application startup time.
**Constraints**: Must integrate with Quarkus CDI and GraalVM native compilation.
**Scale/Scope**: This feature is scoped to the `@NatsSubject` annotation and its runtime processing.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Extension-First Architecture | ✅ PASS | The proposed changes will be implemented within the existing `runtime` and `deployment` modules. |
| II. Minimal Runtime Dependencies | ✅ PASS | No new runtime dependencies are required. |
| III. Test-Driven Development | ✅ PASS | New unit and integration tests will be added to cover the feature. |
| IV. Java 21 Compatibility | ✅ PASS | All new code will be Java 21 compatible. |
| V. CloudEvents Compliance | ✅ PASS | This feature is orthogonal to CloudEvents and does not affect compliance. |
| VI. Developer Experience First | ✅ PASS | This feature directly improves developer experience by providing an annotation-driven API. |
| VII. Observability First | ✅ PASS | This feature is orthogonal to observability and does not affect it. |

## Project Structure

### Documentation (this feature)

```text
specs/003-nats-subject/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── annotation-contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

The project structure is already defined as a multi-module Maven project. The changes will be applied to the following modules:

```text
runtime/
└── src/main/java/org/mjelle/quarkus/easynats/
    ├── NatsSubject.java              # The new annotation
    └── NatsPublisherRecorder.java    # The CDI producer will be here

deployment/
└── src/main/java/org/mjelle/quarkus/easynats/deployment/
    └── QuarkusEasyNatsProcessor.java # Build step for bean registration

integration-tests/
└── src/test/java/org/mjelle/quarkus/easynats/it/
    └── NatsSubjectIntegrationTest.java # New integration test
```

**Structure Decision**: The existing multi-module Maven structure for Quarkus extensions will be used.

## Complexity Tracking

No violations to the constitution were identified.