# Project Overview

This project is a Quarkus extension for EasyNATS, a JetStream NATS messaging library. The extension is intended to provide seamless integration with Quarkus, including support for CloudEvents.

Based on the file structure and content, this project is in the early stages of development and does not yet contain any concrete implementation. The core logic for the extension is yet to be added to the `runtime` and `deployment` modules.

The project is structured as a multi-module Maven project:
- `deployment`: Contains the build-time processor for the Quarkus extension.
- `runtime`: Is intended to contain the runtime logic for the extension, but is currently empty.
- `integration-tests`: Contains integration tests for the extension, which are also currently in a template state.

# Building and Running

The project can be built using Maven. The following commands can be used to build and test the project:

```bash
# Build the project and run the tests
./mvnw clean install

# Run the integration tests
./mvnw clean install -Pit
```

# Development Conventions

The project follows the standard conventions for Quarkus extensions. The code is written in Java and uses Maven for dependency management and building.

The project uses the standard Quarkus testing framework for integration tests. The tests are located in the `integration-tests` module.
