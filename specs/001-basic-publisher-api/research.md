# Research: Basic NatsPublisher API (MVP)

**Date**: 2025-10-25
**Scope**: MVP publisher implementation with minimal feature set

## Technical Decisions

### 1. Quarkus Extension Architecture Pattern

**Decision**: Implement as a Quarkus extension following the multi-module Maven structure (runtime + deployment).

**Rationale**:
- Quarkus extensions separate compile-time (deployment) and runtime concerns for clean native image compilation
- Multi-module structure aligns with Principle I (Extension-First Architecture)
- Deployment module can register CDI beans without runtime overhead

**Alternatives Considered**:
- Direct CDI bean in a single module (rejected: breaks extension pattern, harder to integrate with other Quarkus tools)
- Standalone NATS wrapper library (rejected: not a Quarkus extension, misses dependency injection opportunity)

**Implementation Approach**:
- `deployment/` module contains `QuarkusEasyNatsProcessor` with `@BuildStep` to register NatsPublisher bean
- `runtime/` module contains `NatsPublisher` and `NatsConnectionManager` classes
- Uses Quarkus Arc (CDI) for injection; no external DI frameworks

---

### 2. Singleton NATS Connection Management

**Decision**: Maintain exactly one NATS JetStream connection per Quarkus application, managed as an Arc singleton.

**Rationale**:
- Single connection optimizes network overhead, memory, and latency (Principle II rationale)
- Lifecycle tied to Quarkus application startup/shutdown via lifecycle observing methods
- Simplifies publisher implementation: no connection pooling or manual lifecycle

**Alternatives Considered**:
- Per-publisher connection pooling (rejected: wastes resources, breaks Principle II <500KB constraint)
- Lazy initialization on first publish call (rejected: unpredictable latency, harder to test)

**Implementation Approach**:
- `NatsConnectionManager` class wraps io.nats.client.JetStream connection
- Registered as Arc singleton via `@Singleton` annotation
- Startup: connect to NATS broker (fail fast if unavailable)
- Shutdown: gracefully close connection

---

### 3. Simple String-Based Publisher API

**Decision**: `NatsPublisher` class with single method: `publish(String message)`.

**Rationale**:
- MVP scope: string messages only, hardcoded subject "test"
- Minimizes learning curve for developers
- Validates core extension mechanics before adding generics/configuration

**Alternatives Considered**:
- Generic `NatsPublisher<T>` (rejected: adds complexity, not required for MVP)
- Configurable subject via annotation (rejected: deferred to next feature)

**Implementation Approach**:
```java
public class NatsPublisher {
    public void publish(String message) throws Exception {
        // Inject NatsConnectionManager singleton, publish to hardcoded "test" subject
    }
}
```

---

### 4. Hardcoded Subject: "test"

**Decision**: Subject name is hardcoded to `"test"` in the publisher.

**Rationale**:
- MVP simplicity: no configuration framework needed
- Sufficient for proof-of-concept and integration testing
- Future feature will add `@NatsSubject` annotation for dynamic subject configuration

**Alternatives Considered**:
- Property file configuration (rejected: adds complexity, overkill for MVP)
- Constructor parameter (rejected: still requires configuration, defers DI clarity)

---

### 5. Test Strategy: Unit + Integration

**Decision**: Combine unit tests (publisher in isolation) with integration tests (against real NATS broker).

**Rationale**:
- Unit tests: verify publish logic, mocking the connection
- Integration tests: validate end-to-end behavior with @QuarkusTest and local NATS broker (Docker Compose)
- Aligns with Principle III (Test-Driven Development) and quality gate (>80% coverage)

**Tools**:
- JUnit 5 (via Quarkus)
- Testcontainers or Docker Compose for local NATS broker
- @QuarkusTest for Quarkus application lifecycle integration

**Implementation Approach**:
- **Base test class**: `BasicPublisherTest.java` in `integration-tests/src/main/` uses `@QuarkusTest` (dev mode, contains reusable test methods)
- **Full integration test**: `BasicPublisherIT.java` in `integration-tests/` uses `@QuarkusIntegrationTest`, extends `BasicPublisherTest`, publishes to real NATS broker via docker-compose, verifies receipt

---

### 6. Dependency: io.nats:jnats

**Decision**: Use the official NATS Java client (io.nats:jnats) for JetStream API access.

**Rationale**:
- Industry standard, maintained by the NATS project
- JetStream support (required per Principle II)
- Handles failover, reconnection, and message delivery semantics
- Active maintenance and security updates

**Alternatives Considered**:
- Custom NATS protocol implementation (rejected: reinvents the wheel, security risk)
- Alternative JVM NATS client (rejected: no viable alternatives with equivalent JetStream support)

**Version**: Latest stable (aligned with parent pom.xml Maven property)

---

### 7. Error Handling: Deferred

**Decision**: MVP publishes a method `publish(String message) throws Exception`. Advanced error handling is deferred.

**Rationale**:
- Simplicity first: focus on happy path
- Exception propagation to caller allows application-level handling
- Future features can add retry logic, dead-letter handling, etc.

**Deferred**:
- Automatic retry logic
- Dead-letter queue routing
- Detailed error classification (transient vs. permanent failures)

---

### 8. Docker Compose for Development & Integration Testing

**Decision**: Use existing `integration-tests/docker-compose-devservices.yml` to provide NATS broker and observability stack for local development and integration testing.

**Rationale**:
- Docker Compose already available in project (devservices.yml exists)
- Provides reproducible, isolated development environment
- Includes observability stack (LGTM: Grafana + OpenTelemetry) for future tracing features
- Developers can manually start/stop with: `docker-compose -f integration-tests/docker-compose-devservices.yml up -d`
- No additional setup required beyond Docker installation

**Existing Stack** (in `integration-tests/docker-compose-devservices.yml`):
```yaml
services:
  nats:
    image: nats:2.11
    ports: 4222 (NATS), 8222 (monitoring)
    credentials: guest/guest
    command: -js (JetStream enabled)
  lgtm:
    image: grafana/otel-lgtm
    ports: 3000 (Grafana), 4317/4318 (OpenTelemetry)
```

**Alternative Considered**:
- Testcontainers library (rejected: adds test dependency, Docker Compose already exists)
- Single Docker run commands (rejected: less reproducible, no observability)

**Implementation Approach**:
- Reuse existing `integration-tests/docker-compose-devservices.yml` (no new file created)
- Quickstart.md documents three options: Docker Compose (recommended, via devservices.yml), manual Docker, binary
- Integration tests use existing docker-compose file as dependency (via `-Pit` profile)
- Developers see credentials in documentation (guest/guest for MVP)

---

### 9. Stream Creation via NATS CLI (Manual for MVP)

**Decision**: Require developers to manually create the "test" stream using NATS CLI during initial setup.

**Rationale**:
- MVP focus: explicit stream creation clarifies that JetStream requires streams for durability
- Testing clarity: ensures predictable test environment (explicit stream configuration)
- Developer education: teaches NATS fundamentals (streams, subjects, retention policies)
- No auto-creation burden: avoids adding stream creation logic to extension at MVP stage

**Stream Configuration** (created via CLI):
```bash
nats stream add test_stream \
  --subjects test \
  --discard old \
  --max-age=-1 \
  --replicas=1
```

**Configuration Details**:
- Stream name: `test_stream` (explicit naming convention)
- Subject: `test` (matches hardcoded publisher subject)
- Retention: `discard old` (LIFO - keeps newest messages)
- No expiration (max-age=-1 means keep forever)
- Single replica (suitable for local development)

**Future Enhancement**:
- v0.2 may add automatic stream creation if missing at startup
- Would still allow overrides via configuration

**Alternatives Considered**:
- Extension auto-creates stream at runtime (rejected: MVP scope, adds complexity, less educational)
- Provide helm/k8s manifests (rejected: beyond MVP scope, focus on NATS CLI simplicity)
- Declarative annotation-based stream config (rejected: future feature, not MVP)

---

## Summary of Unknowns Resolved

| Unknown | Resolution | Notes |
|---------|-----------|-------|
| Extension architecture pattern | Quarkus multi-module (runtime/deployment) | Per Principle I |
| Connection management | Singleton, Arc-managed | Per Principle II constraint on resources |
| Publisher API shape | `publish(String)` simple method | MVP only, generics deferred |
| Subject configuration | Hardcoded "test" | MVP only, @NatsSubject annotation deferred |
| Testing strategy | Unit + integration with real NATS | Per Principle III and quality gates |
| NATS client library | io.nats:jnats (official) | Industry standard, JetStream support |
| Error handling | Basic exception propagation | Advanced error handling deferred |
| Dev environment / Integration testing | Docker Compose (devservices.yml) | Reproducible, includes observability stack |
| Stream creation | Manual via NATS CLI (`nats stream add`) | MVP clarity; auto-creation deferred to v0.2 |

## Next Steps (Phase 1: Design & Contracts)

- Generate data-model.md with entity definitions
- Create API contract (minimal OpenAPI spec for publish endpoint)
- Generate quickstart.md for MVP usage
- Update agent context files (CLAUDE.md)
