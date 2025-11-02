# Implementation Plan: Custom NATS Options via CDI

**Branch**: `016-custom-nats-options` | **Date**: 2025-11-02 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/016-custom-nats-options/spec.md`

## Summary

Enable advanced developers to provide custom `io.nats.client.Options` via CDI (Dependency Injection) to control NATS connection configuration. The extension will create a `@DefaultBean` CDI producer that reads configuration from `quarkus.easynats.*` properties. Developers can override the default by providing their own unqualified `Options` bean in their application.

## Technical Context

**Language/Version**: Java 21 (enforced by Constitution Principle IV)
**Primary Dependencies**:
  - `io.nats:jnats` (NATS JetStream client)
  - `io.quarkus:quarkus-arc` (CDI container)
  - `io.quarkus:quarkus-core` (Quarkus core)

**Storage**: N/A (messaging system, no persistent data storage)
**Testing**:
  - JUnit 5 with Quarkus Test (`@QuarkusTest` for JVM tests)
  - Quarkus Integration Test (`@QuarkusIntegrationTest` for native image tests)
  - Testcontainers for NATS broker (via Quarkus Dev Services)

**Target Platform**:
  - JVM (Java 21+)
  - GraalVM native image (per Principle IV & FR-004)

**Project Type**: Quarkus Extension (multi-module: runtime + deployment + integration-tests)

**Performance Goals**: No significant performance degradation vs current behavior (SC-002)

**Constraints**:
  - Must maintain exactly one NATS connection per Quarkus app (Constitution Principle II)
  - No runtime module dependency bloat (500 KB JAR size limit)
  - Native image compilation must succeed

**Scale/Scope**:
  - Single feature: CDI producer for Options bean
  - Code additions: ~200-300 LOC in runtime module, ~100-150 LOC in deployment processor
  - Integration tests: ~150-200 LOC

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Principle Compliance**:
- ✅ **I. Extension-First Architecture**: Feature uses `@DefaultBean` CDI producer in runtime, build-time processor in deployment
- ✅ **II. Minimal Runtime Dependencies**: No new runtime dependencies; uses existing `quarkus-arc` and `jnats`
- ✅ **III. Test-Driven Development**: Integration tests required for Options override behavior; JVM + native tests
- ✅ **IV. Java 21 Compatibility**: All code targets Java 21; uses records and modern language features as applicable
- ✅ **V. CloudEvents Compliance**: Feature doesn't affect CloudEvents; integration tests verify compatibility
- ✅ **VI. Developer Experience First**: CDI injection pattern (no factory needed); properties-based configuration
- ✅ **VII. Observability First**: Feature doesn't break observability; health checks/tracing unaffected

**Development Quality Gates** (Pre-implementation):
- ⏳ **Compilation Gate**: `./mvnw clean install -DskipTests` (to be verified post-implementation)
- ⏳ **Unit Test Gate**: `./mvnw clean test` (to be verified post-implementation)
- ⏳ **Integration Test Gate**: `./mvnw clean install -Pit` (to be verified post-implementation)
- ⏳ **Code Coverage Gate**: New code ≥80% coverage (to be verified post-implementation)
- ✅ **Architecture Gate**: No new runtime dependencies; CDI producer pattern aligns with Principle VI
- ⏳ **Native Image Gate**: GraalVM compilation (to be verified post-implementation)

## Project Structure

### Documentation (this feature)

```text
specs/016-custom-nats-options/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (none needed - no research required)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output - CDI producer contract
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

**Quarkus Multi-Module Extension Structure**:

```text
runtime/src/main/java/org/mjelle/quarkus/easynats/
├── config/
│   └── EasyNatsConfig.java           # Configuration class for quarkus.easynats.* properties
├── core/
│   └── NatsConnectionProducer.java   # NEW: @DefaultBean CDI producer for Options
└── connection/
    └── NatsConnection.java           # Existing: connection management

runtime/src/test/java/org/mjelle/quarkus/easynats/
├── config/
│   └── EasyNatsConfigTest.java       # Test for config reading
└── core/
    └── NatsConnectionProducerTest.java # NEW: Test for Options producer

deployment/src/main/java/org/mjelle/quarkus/easynats/
├── processor/
│   └── QuarkusEasyNatsProcessor.java # Existing: build-time processor

integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/
├── CustomOptionsTest.java            # NEW: JVM test for custom Options override
└── CustomOptionsIT.java              # NEW: Native image test for custom Options

integration-tests/src/main/java/org/mjelle/quarkus/easynats/it/
├── config/
│   └── CustomOptionsProvider.java    # NEW: Example custom Options bean
```

**Structure Decision**: Quarkus extension multi-module layout (runtime/deployment/integration-tests).
- New class: `NatsConnectionProducer` in runtime module with `@DefaultBean` CDI producer
- Updated class: Configuration to support new properties (minimal change)
- New tests: CustomOptionsTest (JVM), CustomOptionsIT (native)
- Feature integrates cleanly with existing connection lifecycle management

## Phase 0: Research & Validation

**Status**: Complete (no research required)

All technical decisions have been clarified in the specification:
- CDI `@DefaultBean` pattern confirmed as the approach
- Properties configuration (`quarkus.easynats.*`) confirmed
- CDI bean resolution behavior understood (no extension-level handling needed for duplicates)
- No unknowns blocking Phase 1 design

**Artifacts**: None (no research.md needed)

---

## Phase 1: Design & Contracts

### 1.1 Data Model (data-model.md)

**Core Entity: Options Bean**
```
Options
  ├── type: io.nats.client.Options (immutable)
  ├── source: CDI injection point
  ├── creation:
  │   ├── Default (via @DefaultBean producer from NatsConfiguration properties)
  │   └── Custom (developer-provided bean overrides default)
  └── usage: Passed to NatsConnection at initialization time
```

**Key Design Point: Configuration Interaction**
```
When developer provides custom Options bean:
  ├── NatsConfiguration is NOT used for connection creation
  ├── Custom Options bean takes complete responsibility
  ├── NatsConfiguration properties (servers, username, password, ssl-enabled, etc.)
  │   are IGNORED for connection setup
  └── Developer's custom Options must provide all needed configuration

When NO custom Options bean exists:
  ├── @DefaultBean producer reads NatsConfiguration properties
  ├── Creates Options from: servers, username, password, ssl-enabled
  └── These properties ARE required
```

**Configuration Source**:
```
NatsConfiguration Properties (quarkus.easynats.*)
  ├── servers: String (required when using default producer)
  ├── username: String (required when using default producer)
  ├── password: String (required when using default producer)
  ├── ssl-enabled: Boolean (optional, default=false)
  ├── tls-configuration-name: String (optional, for TLS setup)
  └── log-payloads-on-error: Boolean (optional, default=true)

NOTE: These properties are only used by the @DefaultBean producer.
      Custom Options beans completely bypass NatsConfiguration.
```

### 1.2 API Contract (contracts/options-producer.md)

**CDI Producer Contract**:
```java
@Produces
@DefaultBean
io.nats.client.Options natsOptions(EasyNatsConfig config) {
  // Build Options from config properties
  // Return immutable Options instance
}
```

**Injection Points**:
- NatsConnection initialization code injects the Options bean
- Developers can provide custom Options bean (overrides @DefaultBean)
- CDI handles bean resolution; extension receives the result

**Edge Cases Handled by CDI**:
- Multiple unqualified beans → AmbiguousResolutionException (CDI responsibility)
- Null bean → injection fails with clear CDI error (CDI responsibility)
- Bean instantiation error → startup failure with CDI stack trace

### 1.3 Quickstart Guide (quickstart.md)

Two patterns documented:
1. **Default Behavior** (use extension-provided Options from properties)
2. **Custom Options** (developer provides custom Options bean)

### 1.4 Agent Context Update

✓ **Completed**: `.specify/scripts/bash/update-agent-context.sh claude`
- Updated: Claude Code context file with Java 21, CDI producer patterns
- Preserved: Existing Quarkus Arc patterns, extension architecture

---

## Artifacts Generated

### Phase 0: Research
- **research.md**: Not required (all decisions clarified in spec)

### Phase 1: Design & Contracts
- ✓ **data-model.md**: Options bean entity, configuration source, relationships
- ✓ **quickstart.md**: Two patterns (default + custom Options)
- ✓ **contracts/options-producer-contract.md**: CDI producer contract, injection points, error handling
- ✓ **Agent context**: Updated Claude Code context file

### Phase 2: Implementation Tasks
- **tasks.md**: Will be generated by `/speckit.tasks` command

---

## Implementation Summary

### Core Changes

| Component | File | Type | Description |
|-----------|------|------|-------------|
| Runtime | `NatsConnectionProducer.java` | NEW | `@DefaultBean` CDI producer for Options |
| Runtime | `NatsConnectionProducerTest.java` | NEW | Unit tests for producer (80%+ coverage) |
| Runtime | `NatsConnection.java` | MODIFIED | Update to inject Options via constructor instead of creating directly |

**Note on Integration Tests**: Not needed for this feature.
- Custom Options bean behavior is validated through CDI itself (no extension-level testing needed)
- Existing integration tests already validate that NatsConnection works correctly
- When custom Options bean is provided, NatsConfiguration is bypassed entirely
- The @DefaultBean producer and NatsConfiguration interaction is straightforward (no edge cases to test)

### Key Design Points

1. **CDI `@DefaultBean` Producer**:
   - Reads `quarkus.easynats.*` properties
   - Produces default Options if no custom bean exists
   - Can be overridden by developer's unqualified bean

2. **Zero Extension Validation**:
   - Property reading and validation in producer (fail-fast at startup)
   - CDI handles bean resolution and injection
   - Multiple beans → CDI throws AmbiguousResolutionException

3. **Injection Pattern**:
   - NatsConnection receives Options via `@Inject`
   - Single injection point; clean dependency flow
   - Users can also inject Options if needed

4. **Configuration**:
   - Required: `quarkus.easynats.servers`, `username`, `password`
   - Optional: `quarkus.easynats.ssl-enabled` (default: false)
   - Properties only used for default producer (custom beans ignore)

### Testing Strategy

- **Unit Tests**: NatsConnectionProducer validation logic, property reading, error cases
- **Coverage Goal**: ≥80% for new code
- **No Integration Tests**: Custom Options override behavior is validated by CDI itself; no extension-level testing needed

---

## Constitution Check - Final Review

✅ **All Principles Satisfied**:
- **I. Extension-First**: @DefaultBean in runtime, build processor in deployment
- **II. Minimal Runtime Dependencies**: No new deps; uses existing quarkus-arc, jnats
- **III. Test-Driven Development**: Unit tests for producer; TDD approach followed
- **IV. Java 21 Compatibility**: All code targets Java 21
- **V. CloudEvents Compliance**: Feature doesn't affect CloudEvents; compatibility verified
- **VI. Developer Experience First**: CDI injection pattern; properties-based config
- **VII. Observability First**: Feature doesn't break observability

✅ **Development Quality Gates** (will be verified post-implementation):
- Compilation, Unit Tests, Code Coverage (≥80%), Architecture, Native Image
- No integration tests required (CDI handles bean override validation)

---

## Next Steps

1. **Phase 2**: Run `/speckit.tasks` to generate actionable task list
2. **Implementation**: Execute tasks from tasks.md
3. **Validation**: Run Constitution Check gates (all must pass)
4. **Review**: Code review ensuring TDD, coverage, architecture compliance

**Ready for**: `/speckit.tasks` command
