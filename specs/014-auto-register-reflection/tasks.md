# Tasks: Automatic Reflection Registration

## Setup Phase

- [X] **Task 1**: Create a new build processor class `EasyNatsProcessor` in the `deployment` module.
- [X] **Task 2**: Add a build step method to the processor that receives the `BuildProducer<ReflectiveClassBuildItem>` and `CombinedIndexBuildItem`.

## Core Implementation Phase

- [X] **Task 3**: Implement the logic to find all methods annotated with `@NatsSubscriber` using the Jandex index.
- [X] **Task 4**: Implement the logic to extract parameter types from the annotated methods, including handling for `List`, `Set`, `Queue`, and `Map` generic types as per the specification.
- [X] **Task 5**: Produce `ReflectiveClassBuildItem`s for all discovered types.

## Testing Phase

- [X] **Task 6**: Create a new integration test to verify the automatic reflection registration. This test should include a subscriber with a custom POJO parameter and run in native mode. (Verified with successful native image tests)

## Documentation Phase

- [X] **Task 7**: Update the project documentation to explain the new automatic reflection registration feature.
