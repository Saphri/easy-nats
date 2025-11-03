# Tasks for Feature: Custom Payload Codec

This document outlines the actionable tasks to implement the custom payload codec feature.

## Phase 1: Setup Public API

- [x] T001 Create package `org.mjelle.quarkus.easynats.codec` in `runtime/src/main/java/`
- [x] T002 Define `Codec` interface in `runtime/src/main/java/org/mjelle/quarkus/easynats/codec/Codec.java`
- [x] T003 Define `SerializationException` in `runtime/src/main/java/org/mjelle/quarkus/easynats/codec/SerializationException.java`
- [x] T004 Define `DeserializationException` in `runtime/src/main/java/org/mjelle/quarkus/easynats/codec/DeserializationException.java`

## Phase 2: Foundational Implementation

- [x] T005 Implement `DefaultCodec` in `runtime/src/main/java/org/mjelle/quarkus/easynats/runtime/codec/DefaultCodec.java`
- [x] T006 Create integration test file `CustomCodecTest.java` in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/`
- [x] T007 [P] Write a failing test in `CustomCodecTest.java` to verify a custom codec is used for publishing and subscribing

## Phase 3: User Story 1 - Register Global Custom Payload Codec

**Goal**: A developer can register a single, global codec to control serialization within the CloudEvent `data` attribute.
**Independent Test**: A test where a custom codec is provided as a CDI bean. A message is published and received, asserting that the custom codec's `encode`, `decode`, and `getContentType` methods were called and the `datacontenttype` header is correct.

- [x] T008 [US1] Modify `NatsPublisher` in `runtime/src/main/java/org/mjelle/quarkus/easynats/NatsPublisher.java` to inject the `Codec` bean
- [x] T009 [US1] Update `NatsPublisher.publish()` to use the `Codec` to encode the payload and set the `datacontenttype`
- [x] T010 [US1] Modify `EasyNatsProcessor` in `deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/EasyNatsProcessor.java` to look up the `Codec` bean
- [x] T011 [US1] Update subscriber invocation logic in `EasyNatsProcessor` to use the `Codec` for decoding
- [x] T012 [US1] Make the integration test from T007 pass

## Phase 4: User Story 2 - Perform Payload Validation & Error Handling

**Goal**: The codec can perform validation, and failures are handled gracefully (NACK).
**Independent Test**: A test where the custom codec's `decode` method throws a `DeserializationException`. The test asserts that the subscriber method is not called.

- [x] T013 [P] [US2] Write a new failing test in `CustomCodecTest.java` where `decode` throws `DeserializationException`
- [x] T014 [US2] In `EasyNatsProcessor`, wrap the call to `codec.decode()` in a try-catch block
- [x] T015 [US2] In the catch block, log the error and generate code to NACK the message
- [x] T016 [US2] Make the integration test from T013 pass

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T017 Update documentation in the `docs/` directory to explain the custom codec feature
- [x] T018 Review all new public APIs and add Javadoc

## Dependencies

-   **US1 depends on**: Phase 1, Phase 2
-   **US2 depends on**: US1

## Parallel Execution Examples

-   **Phase 2**: T007 can be worked on in parallel with the foundational implementation tasks.
-   **Phase 4**: T013 can be worked on in parallel with the implementation tasks for US2.

## Implementation Strategy

The implementation will follow an MVP-first approach. The core logic for discovering and using a custom codec (Phase 3 / US1) will be implemented first. This provides the main feature value. Error handling and validation (Phase 4 / US2) will be implemented next as a distinct, testable increment.
