# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Quarkus extension** for EasyNATS—a JetStream NATS messaging library with CloudEvents support. The project uses a multi-module Maven structure and is currently in early development stages.

### Project Structure

- **Parent Module** (`pom.xml`): Aggregates build configuration, Maven properties (Java 21, Quarkus 3.27.0)
- **Runtime Module** (`runtime/`): Core extension logic, minimal dependencies, produces the extension JAR
- **Deployment Module** (`deployment/`): Build-time processor that integrates with Quarkus's extension framework
- **Integration Tests** (`integration-tests/`): End-to-end tests of the extension; disabled by default (use `-Pit` profile)

### Key Extension Architecture

- **Quarkus Extension Pattern**: The `QuarkusEasyNatsProcessor` in deployment uses Quarkus's `@BuildStep` annotation to register the feature at build time
- **Extension Descriptor**: `runtime/src/main/resources/META-INF/quarkus-extension.yaml` declares the extension metadata
- **Arc Integration**: Runtime module depends on `quarkus-arc` for dependency injection

## Build and Test Commands

```bash
# Build the entire project
./mvnw clean install

# Build without integration tests
./mvnw clean install -DskipTests

# Run all tests including integration tests
./mvnw clean install -Pit

# Run only unit tests (no integration tests)
./mvnw clean test

# Run tests for a specific module
./mvnw -pl runtime clean test
./mvnw -pl deployment clean test

# Run a single test
./mvnw test -Dtest=QuarkusEasyNatsTest

# Clean build artifacts
./mvnw clean

# Check Maven configuration
./mvnw help:effective-pom
```

## Development Notes

### Module Compilation Order

Maven builds in dependency order: `runtime` → `deployment` → `integration-tests`. The deployment module depends on the runtime module, so changes to runtime require recompilation of deployment.

### Extension Processor

The `QuarkusEasyNatsProcessor` class is minimal and only registers a feature build item. As the extension grows, additional build steps will be added here to:
- Register CDI beans
- Configure runtime behavior
- Handle configuration properties

### Integration Tests

Integration tests are in a separate module and use the `-Pit` profile to activate them. These tests validate that the extension works correctly when embedded in a Quarkus application.

### Java Version

The project targets Java 21 (`maven.compiler.release=21`). All code must be compatible with Java 21 features.

### Surefire Configuration

Tests are configured to use `org.jboss.logmanager.LogManager` for logging. System properties are automatically passed to both unit and integration tests.

## Useful Maven Properties

- `quarkus.version`: Currently 3.27.0
- `surefire-plugin.version`: 3.5.2
- `maven.compiler.release`: 21
- Add system properties to tests via `<systemPropertyVariables>` in pom.xml plugins

## Special Considerations

- **Annotation Processing**: Both runtime and deployment modules use `quarkus-extension-processor` during compilation
- **Extension Descriptor**: The `quarkus-extension-maven-plugin` automatically generates extension descriptors at compile time
- **Specification Files**: The `.specify/` directory contains project specification and planning templates (Speckit workflow)

## Active Technologies
- Java 21 (enforced per Principle IV) (001-basic-publisher-api)
- N/A (publisher only; no persistence in this MVP) (001-basic-publisher-api)

## Recent Changes
- 001-basic-publisher-api: Added Java 21 (enforced per Principle IV)
