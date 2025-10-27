# Research: Durable Consumers for @NatsSubscriber

**Feature**: 001-durable-nats-consumers
**Date**: 2025-10-27
**Status**: ✅ COMPLETE (No unknowns to resolve)

## Summary

The technical approach for this feature is well-defined by the existing architecture of the Quarkus EasyNATS extension and the NATS JetStream client library. No significant unknowns were identified. The research focused on validating the chosen approach against best practices for Quarkus extensions and the `jnats` library.

---

## Key Decisions

### 1. Configuration via Annotation Properties

**Decision**: The `stream` and `consumer` name will be specified directly in the `@NatsSubscriber` annotation.

**Rationale**: This approach is simple, explicit, and keeps the configuration co-located with the code that uses it. It aligns with the goal of binding to pre-configured consumers without adding a layer of configuration in `application.properties`.

**Alternatives Considered**:
- **Configuration via `application.properties`**: This was rejected as a "later concern" to keep the initial implementation simple and focused on the core functionality of binding to an existing consumer.

### 2. Fail-Fast on Misconfiguration

**Decision**: The application will fail to start if a `@NatsSubscriber` annotation specifies a durable consumer that does not exist on the NATS server.

**Rationale**: This "fail-fast" approach prevents runtime errors that can be difficult to debug. It ensures that the application is correctly configured before it starts processing messages.

**Alternatives Considered**:
- **Defaulting to an ephemeral consumer**: This was rejected because it could lead to unexpected behavior and message loss if the developer intended to use a durable consumer.

### 3. Consumer Management

**Decision**: The extension will verify that the durable consumer exists on the NATS JetStream server when the application starts. It will not create or configure the consumer.

**Rationale**: This simplifies the extension's responsibility and aligns with the assumption that the NATS environment is pre-configured. The extension's role is to be a client, not a manager, of the NATS infrastructure.

**Alternatives Considered**:
- **Idempotent consumer creation**: This was rejected as a "later concern" to keep the initial scope focused. It could be added as a feature in a future iteration.
