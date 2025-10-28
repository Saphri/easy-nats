# Implementation Plan: Explicit Ack/Nak Control

**Branch**: `009-explicit-ack-nak` | **Date**: 2025-10-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/009-explicit-ack-nak/spec.md`

**Continues**: Feature 008-durable-nats-consumers

**Note**: This plan is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

This feature introduces explicit control over message acknowledgment and negative acknowledgment (nak) in the EasyNATS extension. Developers working with `NatsMessage<T>` wrapper parameters gain full control via `ack()`, `nak(Duration)`, and `term()` methods, enabling advanced error handling patterns (retry, dead-letter queues, conditional processing). The feature is implemented as a thin wrapper around NATS JetStream's native acknowledgment APIs, with no framework-level validation or async enforcement—complete developer responsibility.

## Technical Context

**Language/Version**: Java 21 (enforced per Constitution Principle IV)
**Primary Dependencies**: Quarkus 3.27.0 LTS, NATS JetStream client (io.nats:jnats)
**Storage**: N/A (messaging system; messages managed by NATS JetStream)
**Testing**: Test-Driven Development (TDD) per Constitution Principle III
  - Unit tests in `runtime/src/test/` (target: >80% coverage)
  - Integration tests in `integration-tests/` (e2e Quarkus app validation)
  - Contract tests validating NATS ack/nak/term behavior
**Target Platform**: Quarkus applications (native or JVM)
**Project Type**: Quarkus Extension (multi-module: runtime, deployment, integration-tests per Constitution)
**Architecture Pattern**: Thin wrapper delegation to NATS JetStream APIs
  - `NatsMessage<T>` wraps underlying NATS Message
  - `ack()`, `nak(Duration)`, `term()` are direct pass-through to NATS
  - `payload()` returns pre-deserialized instance (deserialized at construction via existing `MessageDeserializer` class)
  - Reuses `org.mjelle.quarkus.easynats.runtime.subscriber.MessageDeserializer` for Jackson-based type deserialization
**Performance Goals**: Negligible overhead (direct delegation); inherits NATS JetStream latency/throughput
**Constraints**:
  - No framework-level validation of AckPolicy (NATS rejects invalid configs at runtime)
  - No async enforcement; developer responsibility for correct context usage
  - Idempotency guaranteed by pass-through to NATS (NATS handles re-acking)
**Scale/Scope**: Per-message control (no bulk operations)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | Status | Notes |
|-----------|-------------|--------|-------|
| **I. Extension-First Architecture** | Runtime/Deployment split; `@BuildStep` for build-time; META-INF metadata | ✅ Pass | Feature adds methods to `NatsMessage<T>` in runtime module; no deployment-side changes needed for this feature |
| **II. Minimal Runtime Dependencies** | JetStream-only; single connection; no bloat | ✅ Pass | Feature delegates to existing NATS JetStream APIs; no new dependencies; negligible overhead |
| **III. Test-Driven Development** | Unit tests >80% coverage; integration tests; contracts | ✅ Pass | Plan includes unit tests for NatsMessage wrapper, integration tests for ack/nak/term behavior |
| **IV. Java 21 Compatibility** | Java 21 enforced; modern language features encouraged | ✅ Pass | Using Java 21 records for API parameters; no compatibility issues |
| **V. CloudEvents Compliance** | Headers mapping, binary/structured modes | ✅ Pass | Feature preserves CloudEvents headers via `NatsMessage` pass-through; no changes to CE support |
| **VI. Developer Experience First** | Annotation-driven; type-safe `NatsMessage<T>`; minimal config | ✅ Pass | `NatsMessage<T>` parameter pattern enables explicit control without boilerplate; aligns with VI design |
| **VII. Observability First** | W3C tracing; health checks | ✅ Pass | Observability is orthogonal to this feature; tracing/health check headers flow through unchanged |

**Gate Status**: ✅ **PASS** - All Constitution principles satisfied. Feature integrates cleanly with existing extension architecture.

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

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
