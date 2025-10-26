# Implementation Plan: Typed NatsPublisher with CloudEvents Support

**Branch**: `002-typed-publisher` | **Date**: 2025-10-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-typed-publisher/spec.md`

**Note**: This plan is filled in by the `/speckit.plan` command.

## Summary

**MVP 002** extends the MVP 001 `NatsPublisher` class with generic type parameter `<T>` to provide type-safe publishing of domain objects as JSON. Core features:

1. **Typed Publishing**: `NatsPublisher<T>` extends existing class to support publishing any Java type via Jackson JSON serialization
2. **Encoder/Decoder Priority**: Primitives (int, long, String, etc.) and arrays handled natively; Jackson used as fallback for complex types
3. **CloudEvents Support**: Auto-generated headers (ce_type, ce_source, ce_id, ce_time) with ISO 8601 timestamps and UUID IDs
4. **Native Image Ready**: Full `@RegisterForReflection` documentation for GraalVM compilation support

This MVP fulfills **Principle VI (Developer Experience First)** by introducing generic type safety while keeping implementation minimal and focused.

## Technical Context

**Language/Version**: Java 21 (enforced per Principle IV)
**Primary Dependencies**:
- Quarkus 3.27.0 (existing)
- Jackson 2.x (via quarkus-jackson, for complex type serialization)
- NATS JetStream (via jnats 2.23.0, existing)

**Storage**: N/A (messaging extension, no persistent storage)

**Testing**:
- Unit tests via JUnit 5 in runtime module (>80% coverage target)
- Manual integration tests using docker-compose + NATS CLI (no automated integration tests in MVP 002)
- Existing test approach from MVP 001: RestAssured for HTTP endpoint testing

**Target Platform**: Linux/JVM + GraalVM native image (via Quarkus)

**Project Type**: Quarkus extension (runtime + deployment modules)

**Performance Goals**:
- Primitive types (int, long, String): direct encoding with zero Jackson overhead
- Complex types: Jackson serialization with acceptable latency for non-real-time messaging
- Memory: primitive encoder/decoder <1KB, Jackson integration transparent to users

**Constraints**:
- Runtime JAR <500KB (per Principle II)
- Single shared NATS connection per application (per Principle II)
- Native image compatible (requires @RegisterForReflection for complex types)
- CloudEvents spec 1.0 compliance (per Principle V)

**Scale/Scope**:
- Extends existing MVP 001 NatsPublisher class
- Adds generic type parameter and typed encode/decode logic
- ~500-1000 LOC estimated for core typed publisher + CloudEvents support

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principles Evaluation

| Principle | Requirement | Status | Notes |
|-----------|-------------|--------|-------|
| **I. Extension-First** | Runtime/deployment split with @BuildStep | ✅ PASS | MVP 002 extends existing MVP 001 architecture (no changes to runtime/deployment split) |
| **II. Minimal Runtime** | <500KB JAR, single connection, JetStream-only | ✅ PASS | Generic type support + Jackson integration adds ~50KB (estimated); within budget; no new connections introduced |
| **III. TDD** | All changes must have corresponding tests | ✅ PASS | Specification includes test scenarios; implementation will follow red-green-refactor (manual testing + doc examples) |
| **IV. Java 21** | All code targets Java 21 | ✅ PASS | No language version conflicts; generics compatible with Java 21 |
| **V. CloudEvents** | Full spec 1.0 compliance with ce-* headers | ✅ PASS | Specification requires ce_type, ce_source, ce_specversion, ce_id, ce_time headers (FR-003) |
| **VI. Developer Experience** | Generic types + typed publishing (MVP 002 scope) | ✅ PASS | **MVP 002 SCOPED**: Implements `NatsPublisher<T>` generics and CloudEvents support (defers @NatsSubject annotation to future MVP 003) |

### Gate Decision: ✅ PASS

All principles satisfied. MVP 002 scope aligns with constitution Principle VI "MVP 001 Scope Limitation" section:
- ✅ MVP 002 introduces generic types (`NatsPublisher<T>`)
- ✅ MVP 002 adds CloudEvents support
- ✅ MVP 002 defers @NatsSubject annotation pattern to future MVP
- ✅ No backward compatibility required (fresh extension of MVP 001)

**Approved for Phase 0 Research**

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

Quarkus extension structure with runtime and deployment modules:

```text
runtime/src/main/java/org/mjelle/quarkus/easynats/
├── NatsConnectionManager.java      # [MVP 001 - existing]
├── NatsPublisher.java               # [MVP 002: ADD generics + typed publish]
├── TypedPayloadEncoder.java         # [MVP 002: NEW - encoder/decoder resolution]
├── CloudEventsHeaders.java          # [MVP 002: NEW - CloudEvents header factory]
└── CloudEventsPayload.java          # [MVP 002: NEW - CloudEvents wrapper]

runtime/src/test/java/...           # [MVP 002: Unit tests for TypedPayloadEncoder + CloudEventsHeaders]

deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/
├── QuarkusEasyNatsProcessor.java    # [MVP 001 - existing, no changes for MVP 002]
└── NatsFeature.java                 # [MVP 001 - existing, no changes for MVP 002]

integration-tests/
├── src/main/java/org/mjelle/quarkus/easynats/it/
│   ├── PublisherResource.java       # [MVP 001 - existing]
│   └── TypedPublisherResource.java  # [MVP 002: NEW - test REST endpoint for typed publishing]
│
├── src/test/java/org/mjelle/quarkus/easynats/it/
│   ├── BasicPublisherTest.java      # [MVP 001 - existing]
│   ├── BasicPublisherIT.java        # [MVP 001 - existing]
│   ├── TypedPublisherTest.java      # [MVP 002: NEW - dev-mode tests for typed publishing]
│   └── TypedPublisherIT.java        # [MVP 002: NEW - integration tests]
```

**Structure Decision**: Extends existing Quarkus extension structure with new classes for:
1. **TypedPayloadEncoder**: Handles encoder/decoder resolution (primitives → arrays → Jackson)
2. **CloudEventsHeaders**: Factory for generating and validating CloudEvents headers
3. **CloudEventsPayload**: Wrapper class for typed messages with metadata
4. **TypedPublisherResource**: REST endpoint for integration testing typed publishing
5. **Test classes**: Manual tests for dev-mode and integration validation

## Complexity Tracking

No constitution violations. All principles satisfied without complexity trade-offs. ✅

---

## Phase 0: Research

Generate `research.md` by researching:
- Jackson configuration in Quarkus for native image support
- CloudEvents spec 1.0 header naming conventions
- GraalVM reflection requirements for type erasure at runtime

## Phase 1: Design Artifacts

Generate:
- `data-model.md` - CloudEventsPayload entity, encoder/decoder architecture
- `contracts/typed-publisher-api.md` - REST endpoint specs for TypedPublisherResource
- `quickstart.md` - Code examples for typed publishing and CloudEvents usage
- Update Claude Code context file via `update-agent-context.sh`
