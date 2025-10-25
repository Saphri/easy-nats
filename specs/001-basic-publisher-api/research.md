# Phase 0 Research: Basic NatsPublisher API (MVP)

**Branch**: `001-basic-publisher-api`
**Status**: Complete
**Date**: 2025-10-26

## Overview

This research document consolidates findings for MVP 001 technical decisions. All items are resolved; no blockers remain.

---

## Research Questions & Findings

### 1. Quarkus Extension Architecture Pattern

**Question**: What is the standard pattern for Quarkus extensions (runtime/deployment split)?

**Decision**: Multi-module Maven extension pattern per Quarkus official guide
- **Runtime module**: Contains `NatsPublisher` and `NatsConnectionManager` (production code)
- **Deployment module**: Contains `QuarkusEasyNatsProcessor` with `@BuildStep` annotation
- **Extension descriptor**: `META-INF/quarkus-extension.yaml` in runtime module
- **Rationale**: Separation of concerns ensures clean compile-time vs. runtime boundaries; required for native image compilation and proper CDI integration

**Alternatives Considered**:
- Single monolithic module: Rejected—violates Quarkus ecosystem conventions; breaks native image compilation
- Multiple sub-modules: Rejected—adds complexity for MVP; two-module pattern sufficient

**References**:
- Quarkus building-my-first-extension guide (context7 research completed 2025-10-26)
- Quarkus multi-module Maven structure (verified from project pom.xml)

---

### 2. NATS JetStream vs. NATS Core

**Question**: Should MVP 001 use JetStream APIs or NATS Core pub/sub?

**Decision**: JetStream APIs exclusively (per Principle II)
- **JetStream features**: Durable streams, consumer management, message acknowledgment, delivery guarantees
- **NATS Core limitations**: No persistence, no consumer semantics, no delivery guarantees
- **MVP application**: Stream `test_stream` with subject `test`; publish operation targets stream, not raw subject

**Rationale**:
- JetStream is required for reliable messaging patterns (durable queues, dead-letter routing in future MVPs)
- Aligns with Principle II (JetStream-only constraint explicitly documented)
- Foundation for observability (consumer offsets, redelivery metrics)

**Alternatives Considered**:
- NATS Core simple pub/sub: Rejected—lacks durability and consumer semantics needed for production use
- Hybrid (Core + JetStream): Rejected—adds complexity without benefit for MVP

**References**:
- NATS official documentation (JetStream protocol)
- Constitution.md Principle II rationale

---

### 3. CDI Bean Registration in Quarkus

**Question**: How should `NatsPublisher` be registered as a CDI bean for injection?

**Decision**: Arc-based `@Singleton` with build-time registration
- **NatsConnectionManager**: `@Singleton` Arc bean with `@Observes StartupEvent` lifecycle
- **NatsPublisher**: `@Singleton` Arc bean injected with NatsConnectionManager
- **Registration**: QuarkusEasyNatsProcessor uses `@BuildStep` and `BuildProducer<SyntheticBeanBuildItem>`
- **Timing guarantee**: Connection initialization completes before any `@Inject` field population (Quarkus single-threaded startup)

**Rationale**:
- `@Singleton` ensures exactly one instance per application (shared connection requirement from Principle II)
- `@Observes StartupEvent` ensures deterministic initialization order
- Build-time registration prevents runtime classpath scanning overhead

**Alternatives Considered**:
- Runtime registration via CDI extension: Rejected—less efficient; breaks GraalVM native image assumptions
- Lazy initialization (connection on first publish): Rejected—defers failure detection; contradicts fail-fast requirement

**References**:
- Quarkus Arc CDI documentation
- Clarification Q2 session 2025-10-26 (timing guarantee documented in spec.md)

---

### 4. Exception Handling Strategy

**Question**: Should `publish()` throw checked or unchecked exceptions?

**Decision**: Checked `Exception` (propagate JNats IOException)
- **Method signature**: `public void publish(String message) throws Exception`
- **Failure modes**: Network error, broker down (post-startup), authentication failure
- **Caller responsibility**: Must explicitly catch and handle via try-catch

**Rationale**:
- Checked exception makes failure contract explicit at compile time
- Aligns with JNats library design (throws IOException)
- Encourages developers to handle publish failures consciously (no silent failures)

**Alternatives Considered**:
- Custom `NatsPublishException extends Exception`: Rejected—adds wrapper boilerplate; original IOException is sufficiently descriptive
- `RuntimeException`: Rejected—hides failure requirements; allows careless error handling

**References**:
- Clarification Q1 session 2025-10-26
- JNats JetStream API documentation (throws IOException)

---

### 5. Testing Strategy & Local NATS Broker

**Question**: How should integration tests interact with NATS broker?

**Decision**: Docker Compose with reusable dev services stack
- **Broker setup**: `docker-compose-devservices.yml` at project root (existing; reused)
- **Stack**: NATS 2.11 (with JetStream) + LGTM observability (Grafana/OpenTelemetry)
- **Credentials**: Username `guest`, password `guest`
- **Stream setup**: CLI command `nats stream add test_stream --subjects test` (Phase 1 task T006)
- **Test approach**: TDD—write tests FAIL first, then implement to make pass

**Rationale**:
- Reusing existing compose stack reduces setup duplication
- LGTM stack enables future observability validation (Principle VII)
- CLI-based stream creation keeps MVP simple (no programmatic stream management in MVP 001)

**Alternatives Considered**:
- Embedded NATS server (testcontainers): Rejected—adds dependency; Docker Compose already present
- Mock NATS client: Rejected—contract testing requires real broker interaction

**References**:
- integration-tests/docker-compose-devservices.yml (existing)
- NATS CLI documentation (stream management)
- Constitution.md Principle III (TDD required)

---

### 6. Performance Target Validation

**Question**: How should <100ms publish latency target be measured and validated?

**Decision**: Baseline measurement with statistical rigor
- **Metric**: p50 (median) latency, not p99 or p95
- **Setup**: Localhost NATS broker (Docker), Java 21 runtime
- **Measurement**: End-to-end from `publish()` call to NATS acknowledgment
- **Retries**: Excluded from measurement (measure raw publish time only)
- **Validation**: Integration test with latency assertion (SC-005)

**Rationale**:
- p50 captures typical case, not worst-case (p99 too pessimistic for MVP)
- Localhost eliminates network variance (establishes baseline)
- "Raw time without retries" clarifies methodology (no retry overhead counted)

**Alternatives Considered**:
- p99 latency: Rejected—too pessimistic for MVP; deferred to production validation
- Throughput (msgs/sec): Rejected—not part of MVP success criteria; MVP focuses on functional correctness

**References**:
- plan.md Performance Goals: <100ms
- spec.md SC-005 (clarified via Clarification Q3)

---

## Summary: Ready for Phase 1 Design

All research questions resolved. No NEEDS CLARIFICATION markers remain.

| Area | Status | Notes |
|------|--------|-------|
| Quarkus extension pattern | ✅ Clear | Multi-module Maven structure confirmed |
| NATS API choice | ✅ Clear | JetStream exclusive per Principle II |
| CDI bean registration | ✅ Clear | Arc @Singleton with @Observes startup |
| Exception handling | ✅ Clear | Checked Exception propagates IOException |
| Testing infrastructure | ✅ Clear | Docker Compose stack, TDD approach |
| Performance validation | ✅ Clear | p50 latency measurement on localhost |

**Next**: Phase 1 design artifacts (data-model.md, contracts/, quickstart.md)
