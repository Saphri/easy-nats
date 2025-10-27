# Implementation Plan: Transparent CloudEvent Publisher

**Branch**: `005-transparent-cloudevents` | **Date**: 2025-10-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-transparent-cloudevents/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

This feature will refactor the existing generic `NatsPublisher<T>` to automatically wrap all outgoing messages in the CloudEvents format. The `publish(T payload)` and `publish(String subject, T payload)` methods will be modified to transparently handle CloudEvent encoding. The separate `publishCloudEvent` methods will be removed entirely, simplifying the API and making CloudEvents the default, standard behavior. Additionally, the `CloudEventsHeaders.generateSource()` method will be updated to use the standard `quarkus.application.name` configuration property for the CloudEvent `source` attribute.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Quarkus 3.27.0, NATS JetStream client, Jackson Databind
**Storage**: N/A
**Testing**: JUnit 5, Mockito
**Target Platform**: Any platform supporting Quarkus and Java 21
**Project Type**: Quarkus Extension (multi-module Maven project)
**Performance Goals**: Negligible overhead compared to manual CloudEvent creation.
**Constraints**: Must adhere to the CloudEvents 1.0 specification using the NATS Protocol Binding (headers).
**Scale/Scope**: The changes are localized to the `runtime` module, affecting `NatsPublisher` and `CloudEventsHeaders`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Extension-First Architecture**: PASS. Changes are confined to the `runtime` module as expected.
- **II. Minimal Runtime Dependencies**: PASS. No new runtime dependencies are required.
- **III. Test-Driven Development**: PASS. Existing tests will be rewritten to validate the new behavior.
- **IV. Java 21 Compatibility**: PASS. All code will be Java 21 compliant.
- **V. CloudEvents Compliance**: PASS. This feature makes CloudEvents the default, strongly enforcing this principle.
- **VI. Developer Experience First**: PASS. The API is being simplified by removing redundant methods and making the preferred behavior automatic.
- **VII. Observability First**: PASS. No changes to observability.

## Project Structure

### Documentation (this feature)

```text
specs/005-transparent-cloudevents/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── publisher-api.md # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
```text
# The project structure is already defined as a multi-module Maven project.
# Changes will be focused within the following files:

runtime/
└── src/
    ├── main/
    │   └── java/
    │       └── org/
    │           └── mjelle/
    │               └── quarkus/
    │                   └── easynats/
    │                       ├── NatsPublisher.java         # Methods will be modified/removed
    │                       └── CloudEventsHeaders.java    # generateSource() will be updated
    └── test/
        └── java/
            └── org/
                └── mjelle/
                    └── quarkus/
                        └── easynats/
                            └── NatsPublisherTest.java     # Will be rewritten
```

**Structure Decision**: The existing multi-module Maven structure is correct. All changes will be contained within the `runtime` module, as the necessary build-time support for `@NatsSubject` already exists in the `deployment` module.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A       | N/A        | N/A                                 |