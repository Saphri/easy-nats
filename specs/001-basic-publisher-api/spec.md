# Feature Specification: Basic NatsPublisher API (MVP)

**Feature Branch**: `001-basic-publisher-api`
**Created**: 2025-10-25
**Status**: Draft
**Input**: User description: "Create untyped NatsPublisher as start, without cloudevent format. Only just enough code to post a 'hello' string to a subject called 'test'. keep it simple."

## Clarifications

### Session 2025-10-26

- Q: Publisher exception handling: What exception type should `publish()` throw on failure? → A: Checked `Exception`; propagate JNats IOException as-is. Callers must explicitly handle via try-catch, making failure contract explicit at compile time.
- Q: Startup connection timing guarantee: Is connection ready before NatsPublisher injection? → A: Synchronized guarantee. NatsConnectionManager initializes synchronously on StartupEvent (Quarkus single-threaded startup); connection is guaranteed ready before any NatsPublisher injection. No explicit synchronization needed.
- Q: Should performance target (plan.md mentions <100ms latency) be included in success criteria? → A: Yes, add as SC-005. End-to-end publish latency on localhost ≤ 100 ms (p50; measured without artificial retries). Makes latency testable and prevents regressions.

---

## User Scenarios & Testing

### User Story 1 - Inject and Use a Basic String Publisher (Priority: P1)

A developer embeds the Quarkus EasyNATS extension in their application and wants to publish a simple string message to a NATS subject without CloudEvents wrapping.

**Why this priority**: This is the absolute minimum viable publisher experience. It demonstrates the core injection pattern and proves the extension can send messages to NATS.

**Independent Test**: Can be fully tested by creating a minimal Quarkus app with the extension, injecting a `NatsPublisher`, calling `publish("hello")`, and verifying the message appears in a local NATS broker.

**Acceptance Scenarios**:

1. **Given** a Quarkus application with EasyNATS extension loaded, **When** a developer injects `@Inject NatsPublisher publisher` on a field, **Then** the extension provides a working publisher instance
2. **Given** an injected publisher instance, **When** developer calls `publisher.publish("hello")`, **Then** the message is sent to the default subject
3. **Given** a publisher sending to NATS, **When** a message is published, **Then** a connected NATS client can receive the message from the subject `test`
4. **Given** a message published via the publisher, **When** it arrives at NATS, **Then** it contains exactly the string "hello" with no modifications

### Edge Cases

- What happens if NATS broker is unreachable at startup? (Deferred: error handling strategy TBD in next MVP)
- What if publisher is called before NATS connects? (Deferred: lifecycle/blocking semantics TBD in next MVP)

## Requirements

### Functional Requirements

- **FR-001**: Extension MUST provide a `NatsPublisher` class that can be injected via CDI
- **FR-002**: Injected publisher MUST have a `publish(String message) throws Exception` method. Throws checked `Exception` (IOException or subclass) if publish fails; caller must explicitly handle via try-catch
- **FR-003**: Publisher `publish()` method MUST send the string to NATS subject `test`
- **FR-004**: Message content MUST be the raw string, no CloudEvents wrapping
- **FR-005**: Extension MUST auto-initialize a singleton NATS connection on application startup

### Key Entities

- **NatsConnectionManager**: Singleton Arc bean managing NATS JetStream connection lifecycle
  - Scope: `@Singleton` (exactly one instance per Quarkus application)
  - Initialization: `@Observes StartupEvent` – initializes synchronously during Quarkus application startup
  - Timing guarantee: Initialization completes before any `@Inject` fields are populated; connection is ready for use before NatsPublisher can be injected
  - Method: `getJetStream()` → returns shared JetStream instance for publishing
  - Connection parameters (hardcoded for MVP): `nats://localhost:4222`, credentials `guest/guest`
  - Failure mode: Fail-fast if broker unreachable (throws IOException, aborts application startup)

- **NatsPublisher**: Simple wrapper around NATS JetStream publish API
  - Method: `public void publish(String message) throws Exception` → sends to subject; throws checked Exception if publish fails
  - Injected dependency: NatsConnectionManager (used to obtain JetStream instance)
  - JetStream stream name (hardcoded for MVP): `test_stream` (must exist before startup; created via `nats stream add` CLI)
  - Subject name (hardcoded for MVP): `test` (must be defined in stream; single subject maps to `test_stream`)
  - No type parameters (untyped)
  - No CloudEvents support in this MVP
  - Note: Stream and subject are separate concepts; MVP uses subject `test` within stream `test_stream`. Future MVPs may support multiple subjects per stream.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Integration test passes: inject publisher, publish "hello", receive message from subject `test` on local NATS broker
- **SC-002**: Extension builds and runs with Quarkus 3.27.0, Java 21
- **SC-003**: No external dependencies added to runtime module beyond Quarkus Arc and JNats
- **SC-004**: Code coverage >= 80% for new publisher code
- **SC-005**: End-to-end publish latency on localhost NATS broker ≤ 100 ms (p50; measured without artificial retries). Validates performance baseline for future optimization.

## Constraints & Assumptions

- **Tech Stack**: Java 21, Quarkus 3.27.0, NATS JetStream client (io.nats:jnats) per Principle II (JetStream-only, no NATS Core)
- **Scope**: Publisher only; subscriber deferred to future MVP
- **Subject**: Hardcoded to `test` for simplicity; subject configuration deferred to MVP 002+
- **Message Type**: Strings only; typed generics deferred to MVP 002+
- **CloudEvents**: Not supported in this MVP (deferred per Principle V)
- **Target Platform**: Linux/macOS with local NATS broker (Docker or binary)
- **Connection Failure Behavior (CRITICAL)**: `NatsConnectionManager` MUST fail fast at application startup if NATS broker is unreachable. Application startup will fail with a clear error message (IOException or ConnectException) rather than attempting reconnection or degraded mode. This enforces explicit dependency on broker availability and prevents silent runtime failures.
- **JetStream Mandate**: All publish operations MUST use NATS JetStream APIs exclusively per Principle II. NATS Core pub/sub (subject-only messaging without streams) is explicitly unsupported. JetStream stream `test_stream` with subject `test` MUST exist before `NatsConnectionManager` initialization begins. Verify via integration test setup (see Phase 1 tasks T005–T007).

## Out of Scope (for this MVP)

- Subscriber functionality
- CloudEvents format support
- Message type generics/type safety
- Subject configuration via annotations
- Consumer strategy / durable subscriptions
- Health checks / observability
- Error recovery / reconnection logic
