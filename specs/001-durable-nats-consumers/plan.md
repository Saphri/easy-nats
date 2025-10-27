# Implementation Plan: Durable Consumers for @NatsSubscriber

**Branch**: `001-durable-nats-consumers` | **Date**: 2025-10-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-durable-nats-consumers/spec.md`

## Summary

This feature introduces support for pre-configured durable consumers by updating the `@NatsSubscriber` annotation to include `stream` and `consumer` properties. Developers will be able to bind to durable consumers that are assumed to be pre-configured on the NATS server, enabling message consumption that persists across application restarts. The implementation will focus on validating the existence of the specified consumer at startup and binding the subscriber to it.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Quarkus 3.27.0, NATS JetStream client (jnats)
**Storage**: N/A (State is managed by the NATS JetStream server)
**Testing**: Test-Driven Development (TDD): Unit tests (runtime) and Integration tests (integration-tests)
**Target Platform**: Quarkus applications (native or JVM)
**Project Type**: Quarkus Extension
**Performance Goals**: No significant performance impact on application startup time.
**Constraints**: The implementation must not break existing functionality for ephemeral consumers.
**Scale/Scope**: This feature applies to all `@NatsSubscriber` annotations within a Quarkus application.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Principle I (Extension-First Architecture)**: ✅ PASS - The feature will be implemented within the existing Quarkus extension structure.
**Principle II (Minimal Runtime Dependencies)**: ✅ PASS - No new runtime dependencies are required.
**Principle III (Test-Driven Development)**: ✅ PASS - New unit and integration tests will be created to validate the functionality.
**Principle IV (Java 21 Compatibility)**: ✅ PASS - The implementation will use Java 21.
**Principle V (CloudEvents Compliance)**: ✅ PASS - This feature does not directly interact with the message payload or headers, so it will not affect CloudEvents compliance.
**Principle VI (Developer Experience First)**: ✅ PASS - The feature enhances developer experience by providing a simple, annotation-based way to use durable consumers.
**Principle VII (Observability First)**: ✅ PASS - No impact on existing observability features.

## Project Structure

### Documentation (this feature)

```text
specs/001-durable-nats-consumers/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
├── runtime/                # Extension runtime JAR (deployed to users)
│   └── src/
│       ├── main/
│       └── test/
├── deployment/             # Build-time processor (used during user builds only)
│   └── src/
│       └── main/
└── integration-tests/      # E2E tests validating extension in Quarkus app
    └── src/
        ├── main/
        └── test/
```

**Structure Decision**: The project follows the standard Quarkus extension multi-module structure as mandated by the constitution.

## Complexity Tracking

No violations of the constitution were identified.