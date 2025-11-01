<!--
SYNC IMPACT REPORT (v0.2.0 → v0.2.1 Template Synchronization)
================================================================
- Version: 0.2.0 → v0.2.1 (PATCH: Synchronized development gates with plan template)
- Modified Principles: None
- Added Sections: None
- Removed Sections: None
- Amendment Rationale:
  - The `.specify/templates/plan-template.md` file contained a placeholder for constitution checks. This update populates it with the specific "Development Quality Gates" from this constitution, ensuring consistency.
- Template Status:
  ✅ .specify/templates/plan-template.md - updated
- Deferred Items: None
- Follow-up: None
-->

# Quarkus EasyNATS Extension Constitution

## Core Principles

### I. Extension-First Architecture

All development MUST follow the Quarkus extension pattern. Code organization reflects the
runtime/deployment split: runtime module contains minimal, production-grade code; deployment
module handles build-time processing and integration hooks.

**Non-negotiable Rules**:
- Runtime module MUST have zero dependencies beyond Quarkus Arc (CDI)
- Deployment module MUST use `@BuildStep` annotations for all build-time processing
- Extension metadata MUST be defined in `META-INF/quarkus-extension.yaml`
- Build processor classes MUST be located in `deployment/` module only

**Rationale**: The Quarkus ecosystem depends on clean separation between compile-time and
runtime concerns. Violating this breaks native image compilation and introduces unnecessary
production overhead.

---

### II. Minimal Runtime Dependencies (with JetStream-Only Constraint)

The runtime module produces the artifact deployed in user applications. Every dependency MUST
be justified—if not required for core NATS JetStream messaging functionality, it belongs in
deployment or is excluded entirely.

**Non-negotiable Rules**:
- Runtime module MUST use only NATS JetStream APIs; NATS Core functions MUST NOT be used
- NATS Core pub/sub (subject-only messaging without streams/consumers) is explicitly unsupported
- Runtime module MUST NOT depend on Spring, configuration frameworks, or heavy libraries
- External dependencies MUST be approved before addition (document in build decision)
- Transitive dependency bloat MUST be monitored and minimized
- Runtime JAR size MUST not exceed 500 KB without explicit justification
- Failover, reconnection, DLQ management, and message redelivery handling MUST be delegated
  to the JNats client library; extension MUST NOT reimplement these features
- Extension MUST maintain exactly one NATS connection per Quarkus application instance
- Multiple connections MUST be prevented at the architecture level; connection sharing is
  mandatory across all publishers, subscribers, and message handlers in the application
- The shared connection MUST be managed as a singleton, with lifecycle tied to Quarkus
  application startup/shutdown

**Rationale**: JetStream provides essential features for the DX principle: durable streams,
consumer management, message acknowledgment, and delivery guarantees. NATS Core lacks
persistence and consumer semantics needed for reliable messaging patterns. Operational
concerns like failover, reconnection, and redelivery MUST be handled by the proven JNats
client rather than custom implementation, reducing bugs and complexity. Single connection
sharing optimizes resource usage: prevents network overhead, reduces memory consumption,
improves latency. Multiple connections waste resources and degrade performance. Users
embedding this extension depend on small, predictable runtime footprint. Bloated
dependencies break native compilation and increase app size.

---

### III. Test-Driven Development (NON-NEGOTIABLE)

Tests MUST be written and FAIL before implementation. Red-Green-Refactor cycle is mandatory.
All production code changes MUST have corresponding test coverage.

**Non-negotiable Rules**:
- Unit tests in `runtime/src/test/` for runtime logic (target: >80% coverage)
- Integration tests in `integration-tests/` for extension-in-Quarkus validation
- Contract tests MUST validate NATS client behavior against broker
- All public APIs MUST have at least one passing test demonstrating usage

**Rationale**: Early-stage extension development must build confidence in correctness.
TDD prevents silent failures in integration with Quarkus native image compilation.

---

### IV. Java 21 Compatibility (ENFORCED)

All code MUST target Java 21 (`maven.compiler.release=21`). Modern language features are
encouraged where they improve clarity.

**Non-negotiable Rules**:
- No syntax targeting Java versions < 21
- Records, sealed classes, and pattern matching are encouraged where applicable
- GraalVM native image compilation MUST NOT be broken by language feature use
- Maven configuration MUST enforce Java 21 at build time

**Rationale**: Quarkus 3.27.0 targets Java 21+. Consistent versioning prevents
compatibility issues and allows use of modern, safer language constructs.

---

### V. CloudEvents Compliance

NATS JetStream messaging MUST support the CloudEvents standard specification.
All publish/subscribe operations MUST be able to serialize and deserialize CloudEvents
using the CloudEvents Protocol Binding for HTTP with NATS headers as the carrier.

**Non-negotiable Rules**:
- CloudEvents attributes MUST be stored in NATS message headers with `ce-` prefix:
  - Example: `ce-specversion: 1.0`, `ce-type: com.example.event`, `ce-source: /myapp`
  - All standard CloudEvents attributes (id, source, type, specversion, time, etc.)
    MUST be mapped to corresponding `ce-` header fields per spec
- Binary content mode: payload is raw event data, attributes in `ce-` headers
- Structured content mode NOT used: entire CloudEvents event in payload (JSON/Avro), no `ce-` headers
- Header mapping MUST conform to CloudEvents Protocol Binding specification

**Rationale**: Using headers as carrier format keeps NATS message bodies clean for event
payloads while maintaining full CloudEvents compliance. The `ce-` prefix convention
provides immediate semantic clarity and prevents attribute name collisions.

---

### VI. Developer Experience First

The extension MUST provide intuitive, annotation-driven APIs that reduce boilerplate and
enable developers to work with NATS messaging with minimal configuration. Complexity MUST
be abstracted away while remaining transparent and debuggable.

**Non-negotiable Rules**:
- Message publishers MUST be injectable via `@Inject @NatsSubject("subject-name")` annotations
  (e.g., `@Inject @NatsSubject("orders") NatsPublisher<Order> orderPublisher`)
  - No factory patterns or manual client construction required
  - @NatsSubject specifies the NATS subject as metadata (clean separation)
  - Field type `NatsPublisher<T>` provides compile-time type safety
- Publisher generic type parameter MUST be enforced (e.g., `NatsPublisher<MyEvent>`)
  ensuring type-safe publish operations and IDE autocomplete
- Subscribers MUST be declarable via `@NatsSubscriber` annotation with subject and
  consumer configuration (e.g., `@NatsSubscriber(subject = "orders", consumer = "durable-order-pull-consumer") public void processOrders(Order order)`)
  - subject parameter specifies the NATS subject to listen on
  - consumer parameter specifies the consumer name (enables durability tracking)
- Consumer creation strategy options MUST include:
  - Ephemeral consumers (auto-created, auto-deleted after disconnect)
  - Predefined consumers (named, managed, reused across deployments)
- Subscriber method parameter options MUST support two patterns for ack/nak control:
  - **Implicit mode** (simple payload parameter): `void handle(MyEvent event)`
    - Framework MUST automatically ack the message on successful method return
    - Framework MUST automatically nak the message if method throws exception
    - Simplest case for developers not requiring explicit message control
  - **Explicit mode** (NatsMessage wrapper): `void handle(NatsMessage<MyEvent> message)`
    - Method receives full `NatsMessage<T>` wrapper with payload and metadata
    - Method MUST explicitly call `message.ack()` or `message.nak()` or `message.term()`
    - Enables advanced use cases: conditional acknowledgment, retries, dead-letter routing
- NATS connection configuration MUST require minimal settings (see Configuration Requirements section)
- Stream and consumer configuration MUST be declarative (via annotations or properties)
  and validated at application startup
- Configuration updates MUST be safe:
  - Streams/consumers checked at startup; if missing, MUST be created
  - If configuration changed and update is allowed, changes MUST be applied safely
  - Breaking changes (subject pattern, filter, etc.) MUST require explicit opt-in
- Generated configuration MUST be readable and debuggable (logged at startup)
- API methods MUST have clear, self-documenting names (avoid jargon where possible)
- Advanced use cases MUST support injection of raw `NatsConnection` for direct API access:
  - Developers MAY inject `@Inject NatsConnection` to access low-level JetStream APIs
  - Injected connections MUST be the shared singleton (same as used internally)
  - close() method calls on injected connections MUST be no-ops (ignored safely)
  - Implementation MUST wrap injected connection to prevent accidental closure
  - Developers CAN safely use try-with-resources pattern without closing the connection

**Rationale**: Developers choose Quarkus for its zero-config, convention-over-configuration
approach. NATS complexity (streams, consumers, durable subscriptions) MUST not leak into
application code. Annotations and declarative config enable framework to handle operational
concerns. However, advanced use cases requiring direct NATS API access MUST be supported
via injection. The no-op close() guarantee allows developers to use standard patterns
(try-with-resources) safely while preventing accidental connection shutdown bugs.

---

### VII. Observability First

The extension MUST provide first-class observability integration with Quarkus for distributed
tracing and health monitoring. Cloud-native applications require visibility into messaging
system behavior and connectivity status without requiring custom integration code.

**Non-negotiable Rules**:
- W3C Trace Context headers MUST be propagated across message boundaries:
  - `traceparent` header MUST be extracted from incoming messages and included in publisher calls
  - `tracestate` header MUST be preserved when present
  - Outgoing messages MUST include `traceparent` and `tracestate` to continue trace chains
  - Format MUST follow W3C Trace Context specification (https://www.w3.org/TR/trace-context/)
- Quarkus health integration MUST be provided:
  - Readiness probe MUST report NATS connection status (UP if connected, DOWN if disconnected)
  - Liveness probe MUST verify connection actively (optional but recommended)
  - Health check endpoint (`/q/health`) MUST display NATS extension status
  - Health status MUST be queryable programmatically via Quarkus Health API
- Both observability features MUST be configurable and can be disabled:
  - Configuration property to enable/disable W3C trace propagation (default: enabled)
  - Configuration property to enable/disable health checks (default: enabled)
  - Configuration MUST NOT require code changes to toggle features

**Rationale**: Distributed tracing (via W3C headers) enables developers to correlate
messages across services and understand request flow. Health checks integrate with Quarkus
lifecycle, Kubernetes liveness/readiness probes, and monitoring systems. Both features
are standard Quarkus extensions patterns. Configurability allows users to disable features
if performance-critical or already provided by messaging infrastructure.

---

## Multi-Module Maven Structure

The project enforces a multi-module Maven layout to align with Quarkus extension packaging:

```
├── pom.xml                 # Parent POM: Java 21, Quarkus 3.27.0, shared config
├── runtime/                # Extension runtime JAR (deployed to users)
├── deployment/             # Build-time processor (used during user builds only)
└── integration-tests/      # E2E tests validating extension in Quarkus app
```

**Module Responsibilities**:
- **runtime**: NATS client wrapper, CloudEvents support, minimal Arc beans
- **deployment**: Build processors, configuration handlers, bean registration hooks
- **integration-tests**: Full Quarkus app tests, `-Pit` profile enables them

**Build Order**: Maven enforces dependency-driven order: runtime → deployment → integration-tests

---

## Configuration Requirements

The extension MUST require minimal configuration to establish a NATS connection. All configuration uses standard Quarkus properties format (application.properties or application.yaml).

**Required Settings**:

- **`nats.servers`** (String list): NATS broker addresses
  - Format: comma-separated list of server URLs (e.g., `nats://server1:4222,nats://server2:4222`)
  - No default; MUST be provided by application
  - Fail-fast: application startup MUST fail with clear error if not set

- **`nats.username`** (String): Authentication username for NATS broker
  - No default; MUST be provided by application
  - Fail-fast: application startup MUST fail with clear error if not set

- **`nats.password`** (String): Authentication password for NATS broker
  - No default; MUST be provided by application
  - Fail-fast: application startup MUST fail with clear error if not set

**Optional Settings**:

- **`nats.ssl-enabled`** (Boolean): Enable SSL/TLS for broker connection
  - Default: `false` (unencrypted connection)
  - When `true`, connection uses TLS protocol
  - Future versions MAY add detailed SSL configuration (keystore, truststore, etc.)

**Configuration Examples**:

application.properties:
```properties
nats.servers=nats://localhost:4222
nats.username=admin
nats.password=secret
nats.ssl-enabled=false
```

application.yaml:
```yaml
nats:
  servers: nats://localhost:4222
  username: admin
  password: secret
  ssl-enabled: false
```

**Error Handling**:
- Missing required properties: Application MUST fail at startup with descriptive error message
- Invalid server URL format: Application MUST fail at startup with validation error
- Connection failure: Application MUST fail at startup (fail-fast approach per Principle II)

**Rationale**: Only three required settings minimize the configuration burden for users while
providing essential security (authentication) and connectivity (server list). SSL-enabled defaults
to false for simplicity in development; production deployments can opt-in. Fail-fast at startup
prevents silent failures or runtime surprises.

---

## Development Quality Gates

All code changes MUST pass these gates before merging:

1. **Compilation Gate**: `./mvnw clean install -DskipTests` succeeds
2. **Unit Test Gate**: `./mvnw clean test` all pass (runtime + deployment)
3. **Integration Test Gate**: `./mvnw clean install -Pit` all pass (if feature touched NATS behavior)
4. **Code Coverage Gate**: New code ≥80% coverage (measured by Surefire/JaCoCo)
5. **Architecture Gate**: Verify no runtime module dependencies added without justification
6. **Native Image Gate** (future): GraalVM native image compilation succeeds

---

## Governance

This constitution is the authoritative guide for all development decisions.

**Amendment Process**:
- Changes to principles require discussion and documented rationale
- Version updates follow semantic versioning (MAJOR.MINOR.PATCH)
- All PRs MUST reference which principles they uphold or why they deviate
- Deviations from principles MUST be justified in commit messages and PR descriptions

**Compliance Review**:
- Each sprint/milestone includes manual review of principle compliance
- If violations are discovered, create backlog item to remediate

**Guidance Documentation**:
- Runtime developers reference CLAUDE.md for build commands and architecture patterns
- Extension users reference generated Quarkus docs for API usage

**Version**: 0.2.1 | **Ratified**: 2025-10-25 | **Last Amended**: 2025-11-01