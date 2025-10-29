# Tasks: Distributed Tracing for Messaging Spans

**Version**: 1.0
**Status**: To Do
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

---

## Phase 1: Setup

- [x] T001 [P] Add `quarkus-opentelemetry` dependency to `runtime/pom.xml`.
- [x] T001 [P] Add `quarkus-opentelemetry-deployment` dependency to `deployment/pom.xml`.
- [x] T002 [P] Add `quarkus-opentelemetry` dependency to `integration-tests/pom.xml`.

## Phase 2: Core Tracing Implementation

This phase implements the core logic for creating and propagating traces.

**User Story 1**: As a developer, I want my distributed trace to show messaging spans correctly.

**Independent Test Criteria**:
- A message published via `NatsPublisher` should generate a producer span.
- A message received by a `@NatsSubscriber` should generate a consumer span that is a child of the producer span.
- W3C Trace Context headers (`traceparent`, `tracestate`) must be present in the NATS message headers.

**Tasks**:
- [x] T003 [US1] Create a new package `io.quarkus.easynats.runtime.observability` in the `runtime` module.
- [x] T004 [US1] Create a `NatsTraceService` class in the new package to encapsulate OpenTelemetry span creation and context propagation logic.
- [x] T005 [US1] Inject the OpenTelemetry `Tracer` into `NatsTraceService`.
- [x] T006 [US1] Modify `NatsPublisher` to inject `NatsTraceService` and invoke it to create a producer span before publishing a message.
- [x] T007 [US1] Modify the `EasyNatsProcessor` in the `deployment` module to use `NatsTraceService` to start a consumer span when a `@NatsSubscriber` method is invoked.

## Phase 3: Verification & Documentation

This phase verifies the implementation with integration tests and updates the documentation.

**Tasks**:
- [x] T008 [US1] Create a new integration test class `TracingIT.java` in the `integration-tests` module.
- [x] T009 [US1] Add a test to `TracingIT.java` that publishes a message and verifies that a trace with linked producer and consumer spans is exported to the LGTM backend.
- [x] T010 [US1] Add a test case to `TracingIT.java` to verify the `messaging.message_redelivered` attribute is correctly set on redelivery.
- [x] T011 [US1] Add a test case to `TracingIT.java` to verify that a timed-out message processing attempt results in a span with an error status.
- [x] T012 Update the main `README.md` and `docs/INDEX.md` to include information about the new distributed tracing feature.

---

## Dependencies

- **Phase 1** must be completed before **Phase 2**.
- **Phase 2** must be completed before **Phase 3**.

## Parallel Execution

- Tasks **T001** and **T002** can be executed in parallel.
- Within Phase 3, documentation task **T012** can be worked on in parallel with the testing tasks (**T008-T011**).

## Implementation Strategy

The implementation will follow an MVP-first approach, focusing on the "happy path" scenario of successful message delivery. Once the core tracing functionality is in place and verified, the edge cases for redelivery and timeouts will be added.
