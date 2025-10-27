# Implementation Plan: Typed Serialization with Jackson Integration

**Branch**: `007-typed-serialization` | **Date**: 2025-10-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-typed-serialization/spec.md`

## Summary

Simplify message serialization/deserialization by supporting **only Jackson-compatible types** (POJOs, records, generics with Jackson annotations). Eliminate "magic" through explicit type boundaries: if Jackson can handle it, the library supports it; if not, users wrap the type. Uses default CDI-injected ObjectMapper, no custom configuration.

**Technical Approach**:
- Simplify `MessageDeserializer` to Jackson-only (remove all native type handling)
- Simplify `TypedPayloadEncoder` to Jackson-only (remove native primitive/array encoding)
- Validate types at publisher/subscriber registration using Jackson type introspection
- Clear error messages guide users to wrapper pattern for unsupported types
- CloudEvents binary-mode wrapping remains internal implementation detail

## Technical Context

**Language/Version**: Java 21 (enforced per Constitution Principle IV)
**Primary Dependencies**: Jackson Databind (JSON serialization), NATS JetStream client, Quarkus Arc (CDI)
**Storage**: N/A (messaging system)
**Testing**: JUnit 5 + AssertJ (unit), @QuarkusTest/@QuarkusIntegrationTest (integration), Awaitility (async)
**Target Platform**: Quarkus 3.27.0 LTS, native image compilation required
**Project Type**: Quarkus extension (multi-module: runtime + deployment + integration-tests)
**Performance Goals**: No additional latency vs. existing implementation (Jackson serialization is standard)
**Constraints**: Runtime module < 500 KB JAR (Constitution Principle II)
**Scale/Scope**: Single Quarkus extension feature; applies to all typed publishers/subscribers

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Principle I (Extension-First Architecture)**: ✅ PASS
- Feature isolated to runtime (serialization/deserialization) and deployment (type validation)
- No changes to extension packaging or build-time processing structure required

**Principle II (Minimal Runtime Dependencies)**: ✅ PASS
- Removes unnecessary type-handling code from runtime (simplification)
- Uses only Jackson Databind (already a dependency for CloudEvents support)
- No new external dependencies introduced

**Principle III (Test-Driven Development)**: ✅ PASS
- Existing test structure (004-nats-subscriber-mvp, 006-typed-subscriber) provides foundation
- New tests required for Jackson-only deserialization with type validation

**Principle IV (Java 21 Compatibility)**: ✅ PASS
- Uses only Java 21 compatible features
- Records supported as Jackson-compatible types

**Principle V (CloudEvents Compliance)**: ✅ PASS
- CloudEvents binary-mode carrier format unchanged (internal implementation)
- Feature works with CloudEvents wrapped messages transparently

**Principle VI (Developer Experience First)**: ✅ PASS
- Simplifies API surface: users declare type, library handles serialization
- Clear error messages guide users when types unsupported
- Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize) work out-of-box
- Follows Jackson conventions (predictable, no library-specific magic)

**Principle VII (Observability First)**: ✅ PASS
- Deserialization errors logged with target type and root cause (supports debugging)
- No impact on existing W3C trace context or health check integration

## Project Structure

### Documentation (this feature)

```text
specs/007-typed-serialization/
├── spec.md                      # Feature specification
├── plan.md                      # This file (Phase 0-1 planning output)
├── research.md                  # Phase 0 output (research tasks resolved)
├── data-model.md                # Phase 1 output (type definitions)
├── quickstart.md                # Phase 1 output (usage examples)
├── contracts/                   # Phase 1 output (API definitions)
│   ├── typed-publisher.md       # PublisherType, serialization contract
│   ├── typed-subscriber.md      # SubscriberType, deserialization contract
│   └── errors.md                # SerializationException, DeserializationException
├── checklists/
│   └── requirements.md          # Specification quality validation
└── tasks.md                     # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (Quarkus Extension - Multi-Module)

```text
runtime/src/main/java/org/mjelle/quarkus/easynats/
├── NatsSubscriber.java                        # [UNCHANGED] Annotation definition
├── runtime/
│   ├── handler/
│   │   └── DefaultMessageHandler.java          # [MODIFY] Use Jackson-only deserialization
│   ├── publisher/
│   │   ├── NatsPublisher.java                 # [MODIFY INTERNAL] Public API unchanged, encodePayload() uses Jackson-only
│   │   └── NatsPublisherFactory.java          # [EXISTING] Create publishers
│   └── subscriber/
│       ├── MessageDeserializer.java           # [SIMPLIFY] Jackson-only deserialization
│       ├── DeserializationException.java      # [EXISTING] Error handling
│       ├── TypeValidator.java                 # [NEW] Type validation at registration
│       ├── TypeValidationResult.java          # [NEW] Validation result type
│       ├── SerializationContext.java          # [NEW] Serialization metadata
│       └── DeserializationContext.java        # [NEW] Deserialization metadata
│
├── TypedPayloadEncoder.java                    # [SIMPLIFY] Jackson-only encoding
├── SerializationException.java                # [EXISTING] Error handling
├── MessageType.java                           # [NEW] Generic type wrapper
└── ErrorMessageFormatter.java                 # [NEW] Error message generation

deployment/src/main/java/org/mjelle/quarkus/easynats/
├── deployment/
│   ├── QuarkusEasyNatsProcessor.java          # [EXISTING] Build-time processing
│   ├── TypeValidatorStep.java                 # [NEW] Validate types at build-time
│   └── BeanRegistrationStep.java              # [MODIFY] Register type validators

integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/
├── TypedSerializationTest.java                # [NEW] JVM tests for typed serialization
├── TypedSerializationIT.java                  # [NEW] Native image tests
├── model/
│   ├── OrderData.java                         # [EXISTING] Example POJO
│   ├── Product.java                           # [EXISTING] Example POJO
│   ├── InvalidType.java                       # [NEW] Type without no-arg constructor (for error testing)
│   └── UnsupportedType.java                   # [NEW] Primitive wrapper for testing rejection
└── resources/
    └── application-test.properties             # [MODIFY] Enable typed serialization
```

**Structure Decision**: Quarkus multi-module extension.
- **runtime**: Core serialization/deserialization logic (simplified to Jackson-only)
- **deployment**: Build-time type validation and error reporting
- **integration-tests**: Full Quarkus app validation of typed serialization feature

## Complexity Tracking

No Constitution Check violations. All principles pass. No complexity justification needed.

---

## Phase 0: Outline & Research

**Status**: ✅ COMPLETE (no unknowns to resolve)

### Research Summary

All technical decisions are explicit in the specification and constitution. No NEEDS CLARIFICATION markers remain.

**Key Decisions**:
1. **Jackson-only deserialization**: Removes complexity of native type handling (primitives, arrays, byte[], etc.)
2. **Type validation via introspection**: Uses Jackson's `ObjectMapper.getTypeFactory().constructType()` to validate types at registration without instantiation
3. **Default CDI-injected ObjectMapper**: Simplifies API—users don't configure serialization
4. **CloudEvents binary-mode (internal)**: Users don't interact with transport wrapping
5. **Clear error guidance**: Errors direct users to wrapper pattern for unsupported types
6. **Free-form human-readable logging**: ERROR level logs with type context and Jackson root cause (Clarification from /speckit.clarify)

**Alternatives Considered**:
- **Custom ObjectMapper support**: Rejected because single CDI ObjectMapper simpler and aligns with Quarkus conventions
- **Support for primitives/arrays**: Rejected because violates "less magic" goal and adds complexity; users should wrap unsupported types
- **Runtime type validation**: Rejected in favor of build-time validation where possible; runtime validation provides clear errors at first use
- **Structured logging format**: Rejected in favor of free-form human-readable format that's simpler to implement and maintain

See `research.md` for detailed findings.

---

## Phase 1: Design & Contracts

**Status**: ✅ COMPLETE (design artifacts generated)

### 1. Data Model (`data-model.md` - generated)

Key types defined:
- **MessageType<T>**: Wrapper for generic type with validation metadata
- **TypeValidationResult**: Success/error from type validation with root cause
- **SerializationContext**: Metadata for serialization (ObjectMapper, target type)
- **DeserializationContext**: Metadata for deserialization (target type, raw payload for error logging)
- **Supported Types**: POJOs with no-arg constructor, records, generic types, Jackson-annotated types
- **Unsupported Types**: Primitives, arrays, types without no-arg constructors (unless custom deserializer)

### 2. API Contracts (`contracts/` - generated)

**TypedPublisher Contract** (`contracts/typed-publisher.md`):
- `<T> void publish(T payload)`: Serialize and send typed object
- Type validation at creation time
- Error: Clear message if type unsupported

**TypedSubscriber Contract** (`contracts/typed-subscriber.md`):
- `void handle(T message)`: Implicit mode - auto-ack on success, auto-nak on error
- Type validation at registration
- Error: Clear message if type unsupported, deserialization error includes target type

**Error Contract** (`contracts/errors.md`):
- `SerializationException`: Wraps Jackson serialization failures with type information
- `DeserializationException`: Wraps Jackson deserialization failures with type and raw payload

### 3. Quick Start (`quickstart.md` - generated)

Example: Publish/subscribe to `OrderData` POJO without manual serialization, including:
- Message type definition (POJO and record)
- Publisher implementation
- Subscriber implementation
- REST endpoint example
- Jackson annotations (@JsonProperty, @JsonIgnore, @JsonDeserialize)
- Common patterns and troubleshooting

### 4. Agent Context Update

Run `.specify/scripts/bash/update-agent-context.sh` to register:
- Jackson type introspection patterns
- Quarkus extension type validation patterns
- Error handling patterns

---

## Phase 2: Implementation Tasks

*Generated by `/speckit.tasks` command (64 tasks, T001-T064)*

### Expected Task Areas

1. **Setup & Verification** (Phase 1, 7 tasks)
   - Verify project structure and dependencies ready

2. **Type Validation & Error Handling** (Phase 2, 8 tasks)
   - TypeValidator implementation
   - Context classes (Serialization, Deserialization)
   - MessageType wrapper
   - Error message formatting

3. **Publish/Subscribe Core** (Phase 3, 12 tasks - US1 MVP)
   - Simplify TypedPayloadEncoder (Jackson-only)
   - Simplify MessageDeserializer (Jackson-only)
   - Update NatsPublisher.encodePayload()
   - Update DefaultMessageHandler
   - Integration tests (JVM + native)

4. **Jackson-Only with Clear Errors** (Phase 4, 13 tasks - US2)
   - Type validation at publisher/subscriber registration
   - Unit tests for rejection cases (primitives, arrays, missing constructors)
   - Integration tests for error scenarios

5. **Jackson Annotations Support** (Phase 5, 6 tasks - US3)
   - Test @JsonProperty, @JsonIgnore, @JsonDeserialize support
   - Verify annotations respected in serialization/deserialization

6. **Documentation** (Phase 6, 6 tasks - US4)
   - Jackson Compatibility Guide
   - Wrapper Pattern Tutorial
   - Error Troubleshooting Guide
   - Documentation review

7. **Polish & Integration** (Phase 7, 12 tasks)
   - Comprehensive unit and integration testing
   - Performance validation
   - Regression testing
   - Final documentation review

See `tasks.md` for complete task breakdown.

---

## Success Criteria Mapping

| Spec Criterion | Implementation Focus | Validation Method |
|--------|-------------------|------------------|
| SC-001: Publish/subscribe typed messages without manual serialization | TypedPayloadEncoder + MessageDeserializer simplification | Integration tests (TypedSerializationTest) |
| SC-002: Unsupported types rejected with clear errors | TypeValidator + error message formatting | Unit + integration rejection tests |
| SC-003: Deserialization errors logged with context | DeserializationContext + error logging | Integration test: error logging validation |
| SC-004: Documentation with examples and wrapper patterns | JACKSON_COMPATIBILITY_GUIDE.md + WRAPPER_PATTERN.md | Documentation review + manual verification |
| SC-005: Existing Jackson-annotated types work unmodified | Jackson annotation support in serialization/deserialization | Integration tests for @JsonProperty, @JsonIgnore, @JsonDeserialize |
| SC-006: New developers can follow documentation to publish/subscribe | Quickstart.md + error guidance | Documentation completeness review |

---

## Dependencies & Blockers

### Hard Blockers
- None identified; all dependencies within the extension

### Soft Blockers (require coordination)
- NATS JetStream broker must be running for integration tests (docker-compose-devservices.yml handles this)
- GraalVM required for native image tests (optional but recommended)

### Integration Points
- Jackson Databind: Already a dependency (no new blocker)
- Quarkus Arc: Already a dependency (no new blocker)
- NATS JetStream client: Already a dependency (no new blocker)

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Breaking change for users of 006-typed-subscriber | Medium | Medium | Clear migration guide; wrapper pattern examples |
| Jackson version incompatibility | Low | Low | Use Jackson 2.15.x+ (already enforced by Quarkus 3.27.0) |
| Native image compilation issues | Low | Medium | Test with GraalVM; use @QuarkusIntegrationTest |
| Performance regression | Low | Medium | Benchmark serialization/deserialization latency |
| Incomplete error message clarity | Medium | Medium | Comprehensive unit tests for error scenarios |

---

## Acceptance Criteria

**Feature Complete When**:
1. ✅ All 64 implementation tasks completed (T001-T064)
2. ✅ All unit tests pass (runtime module, >80% coverage)
3. ✅ All JVM integration tests pass (TypedSerializationTest)
4. ✅ All native image tests pass (TypedSerializationIT)
5. ✅ No regressions in existing features (001-006 tests pass)
6. ✅ Documentation complete (guides, examples, troubleshooting)
7. ✅ Build succeeds: `./mvnw clean install`
8. ✅ Runtime JAR < 500 KB (Constitution Principle II)
9. ✅ PR created with sign-off checklist

---

## Timeline Estimate

| Phase | Tasks | Duration | Cumulative |
|-------|-------|----------|-----------|
| Phase 1 (Setup) | T001-T007 | 1-2 hrs | 1-2 hrs |
| Phase 2 (Foundational) | T008-T015 | 4-6 hrs | 5-8 hrs |
| Phase 3 (US1 MVP) | T016-T027 | 6-8 hrs | 11-16 hrs |
| Phase 4 (US2) | T028-T040 | 4-6 hrs | 15-22 hrs |
| Phase 5 (US3) | T041-T046 | 2-3 hrs | 17-25 hrs |
| Phase 6 (US4) | T047-T052 | 4-5 hrs | 21-30 hrs |
| Phase 7 (Polish) | T053-T064 | 6-8 hrs | 27-38 hrs |
| **Total** | **64 tasks** | **~30-40 hrs** | **~30-40 hrs** |

**Parallel Execution** (can reduce to ~25 hrs):
- Phase 5 (US3) and Phase 6 (US4) can run in parallel (no code dependencies)
- Individual unit/integration test tasks can parallelize after foundational tasks

---

## Next Steps

1. **Proceed to Implementation**: Start Phase 1 (Setup & Verification) with tasks T001-T007
2. **Track Progress**: Use `/speckit.tasks` output and mark tasks as in_progress/completed
3. **Test Driven**: Write failing tests first, then implement to pass tests
4. **Review Frequently**: Check Constitution compliance after Phase 3 (US1 MVP)
5. **Document as You Go**: Update quickstart.md and guides incrementally

---

## Sign-Off

**Planning Complete**: 2025-10-27
**Ready for Implementation**: YES
**Constitutional Compliance**: ✅ All 7 principles pass
**Known Risks**: None critical; all mitigated
**Next Command**: Start implementation with task descriptions in `tasks.md`
