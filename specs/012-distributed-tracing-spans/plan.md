# Implementation Plan: Distributed Tracing for Messaging Spans

**Version**: 1.0
**Status**: In Progress
**Author**: AI Assistant
**Last Updated**: 2025-10-29
**Spec**: [spec.md](./spec.md)
**Branch**: `012-distributed-tracing-spans`

---

## 1. Technical Context & Dependencies

### 1.1. Existing Architecture

The project is a Quarkus extension for NATS JetStream. It already includes a `NatsConnectionManager` for managing the NATS connection, a generic `NatsPublisher`, and a `@NatsSubscriber` annotation. The core architecture is in place to intercept message publishing and consumption.

### 1.2. Key Technologies

- **Java 21 & Quarkus 3.27.0**: The core development stack.
- **NATS JetStream (jnats 2.23.0)**: The messaging client library.
- **OpenTelemetry**: The standard for distributed tracing. We will need to integrate with the Quarkus OpenTelemetry extension (`quarkus-opentelemetry`).

### 1.3. External Integrations

- **OpenTelemetry Collector/Backend (e.g., Jaeger, Zipkin, LGTM)**: The extension will generate traces that are sent to an OpenTelemetry-compatible backend. The specific backend is outside the scope of this feature, but the generated traces must be compliant.

### 1.4. Unresolved Decisions

- None. The specification and clarifications have provided sufficient detail to proceed.

---

## 2. Constitution Check

This section validates the proposed implementation against the project's constitution.

| Principle | Adherence | Justification |
|---|---|---|
| **I. Extension-First Architecture** | ✅ Yes | The implementation will be done within the existing Quarkus extension structure, with tracing logic added to the `runtime` module and build-time processing in the `deployment` module if necessary. |
| **II. Minimal Runtime Dependencies** | ✅ Yes | The only new dependency will be `quarkus-opentelemetry`, which is the standard Quarkus way to handle tracing and is a minimal, well-integrated dependency. |
| **III. Test-Driven Development** | ✅ Yes | New integration tests will be added to verify that trace contexts are correctly propagated and that spans are created as expected. |
| **IV. Java 21 Compatibility** | ✅ Yes | All new code will be written in Java 21. |
| **V. CloudEvents Compliance** | ✅ Yes | The implementation will leverage NATS headers to propagate W3C Trace Context, which is compatible with the CloudEvents header-based transport. |
| **VI. Developer Experience First** | ✅ Yes | Tracing will be enabled by default with zero configuration required from the developer, aligning with the "convention over configuration" principle. |
| **VII. Observability First** | ✅ Yes | This feature directly implements the observability principle by adding distributed tracing capabilities. |

---

## 3. Phase 0: Outline & Research

No research is required. The technical path is clear: we will use the Quarkus OpenTelemetry extension to create and manage spans. The integration will involve intercepting publish and subscribe operations to start and stop spans, and to inject/extract the W3C Trace Context (and only W3C Trace Context) from NATS message headers.

**File Generated**: `research.md` (will be minimal)

---

## 4. Phase 1: Design & Contracts

### 4.1. Data Model

No new data models are required for this feature. The key entities are `Trace` and `Span`, which are defined by the OpenTelemetry specification.

**File Generated**: `data-model.md` (will state that no new models are needed)

### 4.2. API Contracts

This feature does not introduce any new public-facing APIs. It enhances the existing `NatsPublisher` and `@NatsSubscriber` by adding tracing capabilities transparently.

**Files Generated**: None in `/contracts/`

### 4.3. Quickstart Guide

A new section will be added to the quickstart guide to explain how distributed tracing works and how to view the traces in a backend like LGTM.

**File Generated**: `quickstart.md`

---

## 5. Phase 2: Implementation & Tasks

The implementation will be broken down into the following tasks:

1.  **Add `quarkus-opentelemetry` dependency**: Add the dependency to the `runtime` and `integration-tests` modules.
2.  **Create a Tracing Service**: Implement a CDI bean that encapsulates the logic for creating producer and consumer spans using the OpenTelemetry `Tracer`.
3.  **Integrate with `NatsPublisher`**: Modify the `NatsPublisher` to use the Tracing Service to start a producer span and inject the trace context into the message headers before publishing.
4.  **Integrate with `@NatsSubscriber`**: Modify the build-time processor for `@NatsSubscriber` to wrap the subscriber method invocation with logic that extracts the trace context from the message headers and starts a consumer span.
5.  **Add Integration Tests**: Create new integration tests to verify:
    - Trace context is propagated from producer to consumer.
    - Producer and consumer spans are created with the correct attributes.
    - Redelivery and timeout scenarios are correctly represented in the trace.
6.  **Update Documentation**: Update the project's documentation to reflect the new tracing capabilities.

---

## 6. Post-Implementation Constitution Check

A final check will be performed after implementation to ensure all principles are still being met. No violations are anticipated.