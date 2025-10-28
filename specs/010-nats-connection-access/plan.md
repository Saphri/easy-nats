# Implementation Plan: NATS Connection Access API

**Branch**: `010-nats-connection-access` | **Date**: 2025-10-28 | **Spec**: `specs/010-nats-connection-access/spec.md`
**Input**: Feature specification from `/specs/010-nats-connection-access/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Expose the shared NATS connection as a CDI-injectable singleton bean with a thin wrapper that:
1. Prevents accidental closure (no-op `close()` method, fail-fast injection timing)
2. Supports try-with-resources idiom via `AutoCloseable` implementation
3. Transparently delegates all NATS operations (publish, subscribe, listener registration) to the underlying jnats connection
4. Is configurable via standard Quarkus properties (servers, username, password, ssl-enabled)
5. Enables future health checks (Feature 011) via listener delegation

**Technical approach**: Build-time processor registers the wrapper as a `@Singleton` CDI bean during Quarkus startup. Runtime wrapper is a thin delegating facade with no-op `close()` and full method delegation using reflection-free composition.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21
**Primary Dependencies**: Quarkus 3.27.0, NATS JetStream client (io.nats:jnats), Quarkus Arc (CDI)
**Storage**: N/A (connection management only, no persistence)
**Testing**: Test-Driven Development (TDD): Unit tests (runtime) and Integration tests (integration-tests)
**Target Platform**: Quarkus applications (native and JVM)
**Project Type**: Quarkus Extension (multi-module: runtime, deployment, integration-tests)
**Performance Goals**: Minimal overhead - wrapper delegation ≤5% additional latency vs direct jnats usage
**Constraints**: No-op close() guarantees (fail-safe); fail-fast injection timing (prevent silent failures); no custom SSL context beyond JVM default
**Scale/Scope**: Single shared connection per Quarkus app; one wrapper singleton; supports unlimited concurrent operations (delegated to jnats)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ Principle I: Extension-First Architecture
- **Status**: PASS
- **Rationale**: Feature uses standard Quarkus extension pattern. Build-time processor registers CDI bean via `@BuildStep` in deployment module. Runtime module contains only connection wrapper (thin facade). No deployment concerns leak into runtime.
- **Implementation**: `deployment/QuarkusEasyNatsProcessor.java` will register `ConnectionWrapper` bean; `runtime/NatsConnectionWrapper.java` implements the facade.

### ✅ Principle II: Minimal Runtime Dependencies (JetStream-Only)
- **Status**: PASS
- **Rationale**: Feature adds zero new runtime dependencies. Uses existing jnats client (already required by extension). No new heavyweight libraries. Connection wrapper is <100 LOC, pure delegation. Single connection enforced at architecture level (one shared instance).
- **Implementation**: Wrapper class delegates all operations to jnats; no reimplementation of reconnection, failover, or message handling.

### ✅ Principle III: Test-Driven Development
- **Status**: PASS
- **Rationale**: TDD is mandated. Unit tests will verify wrapper delegation (thread-safety, no-op close). Integration tests will verify CDI injection, configuration, try-with-resources, and connection sharing across multiple beans.
- **Implementation**: Tests in `runtime/src/test/` and `integration-tests/src/test/` with >80% coverage target.

### ✅ Principle IV: Java 21 Compatibility
- **Status**: PASS
- **Rationale**: Feature uses only Java 21-compatible syntax. Wrapper may use records for immutable configuration, sealed classes for type safety, or pattern matching for delegation. No language features break native image compilation.
- **Implementation**: Maven enforces `maven.compiler.release=21`.

### ✅ Principle V: CloudEvents Compliance
- **Status**: PASS (not violated; not required for this feature)
- **Rationale**: Connection access feature does not implement CloudEvents handling. CloudEvents support is already in place (Feature 005); this feature merely exposes raw connection for advanced use. Future features using this connection (e.g., health checks) will inherit CloudEvents support.
- **Implementation**: No CloudEvents code in wrapper; feature is transparent to CloudEvents processing.

### ✅ Principle VI: Developer Experience First
- **Status**: PASS
- **Rationale**: Feature provides intuitive CDI injection (`@Inject Connection`), try-with-resources support, and safe failure semantics (no-op close, fail-fast injection). Developers can directly access raw jnats APIs without manual connection management. Wrapper is invisible—it "just works."
- **Implementation**: `ConnectionWrapper` implements `AutoCloseable` with no-op close. CDI processor registers singleton. Constructor injection supported via Quarkus CDI.

### ✅ Principle VII: Observability First
- **Status**: PASS (not violated; enabled for future)
- **Rationale**: This feature enables (not violates) observability. By delegating listener registration methods, Feature 011 (health checks) can register `ConnectionListener` for NATS state monitoring. W3C trace headers are handled by existing publisher/subscriber (Feature 007); raw connection access doesn't require changes.
- **Implementation**: Wrapper delegates `setConnectionListener()` and other listener methods; health check feature (011) will register listeners via injected connection.

### ✅ Conclusion
**All gates PASS.** Feature aligns with all seven principles. No violations. No exceptions needed.

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
