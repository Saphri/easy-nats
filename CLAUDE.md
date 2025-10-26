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

## Coding Guidelines (MANDATORY)

### Dependency Injection

- **Constructor Injection REQUIRED**: All dependencies must be injected via constructor parameters, NOT fields
- **@Inject Annotation**: ONLY allowed in test classes (@QuarkusTest, @QuarkusIntegrationTest)
- **Production Code**: Never use `@Inject` field injection in runtime or deployment modules
- **Rationale**: Constructor injection enables immutability, testability, and explicit dependency declaration

**Example (CORRECT)**:
```java
@Singleton
public class NatsPublisher {
    private final NatsConnectionManager connectionManager;

    NatsPublisher(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
}
```

**Example (WRONG)**:
```java
@Singleton
public class NatsPublisher {
    @Inject NatsConnectionManager connectionManager;  // ❌ NEVER in production code
}
```

### Testing Assertions

- **AssertJ REQUIRED**: All assertions must use AssertJ (import `static org.assertj.core.api.Assertions.*`)
- **JUnit Assertions FORBIDDEN**: Never use `org.junit.jupiter.api.Assertions` or `JUnit.assert*`
- **Rationale**: AssertJ provides fluent, readable assertions with better error messages

**Example (CORRECT)**:
```java
@Test
void testPublisherCanBeInjected() {
    assertThat(publisher).isNotNull();
    assertThat(publisher.getClass().getSimpleName()).isEqualTo("NatsPublisher");
}
```

**Example (WRONG)**:
```java
@Test
void testPublisherCanBeInjected() {
    assertNotNull(publisher);  // ❌ JUnit assertion; use AssertJ instead
    assertEquals("NatsPublisher", publisher.getClass().getSimpleName());
}
```

### REST Response Handling

- **Never use `jakarta.ws.rs.core.Response`**: Do not use Response class directly in controllers
- **Use Quarkus REST Response Types**: Return POJOs and let Quarkus serialize them, or use `io.quarkus.rest.common.runtime.Response` equivalents
- **Rationale**: Keeps REST endpoints declarative and decoupled from HTTP response building

**Example (CORRECT - Quarkus REST with POJO)**:
```java
@POST
@Path("/publish")
public PublishResult publish(PublishRequest request) {
    publisher.publish(request.getMessage());
    return new PublishResult("success", request.getMessage());
}
```

**Example (CORRECT - Using Jackson/@JsonResponse)**:
```java
@POST
@Path("/publish")
@Produces(MediaType.APPLICATION_JSON)
public PublishResult publish(PublishRequest request) {
    publisher.publish(request.getMessage());
    return new PublishResult("success", request.getMessage());
}
```

**Example (WRONG)**:
```java
@POST
@Path("/publish")
public Response publish(PublishRequest request) {
    try {
        publisher.publish(request.getMessage());
        return Response.ok(new PublishResult("success")).build();  // ❌ Avoid Response builder
    } catch (Exception e) {
        return Response.serverError().entity(e.getMessage()).build();
    }
}
```

### Async Testing with Awaitility

- **Never use `Thread.sleep()` or `wait()`**: Use Awaitility library for async testing
- **Awaitility Required**: Import `org.awaitility.Awaitility.*`
- **Rationale**: Awaitility handles timing, retries, and timeouts more reliably than sleep; tests run faster

**Example (CORRECT)**:
```java
@Test
void testMessageAppearsOnBroker() throws Exception {
    publisher.publish("hello world");

    // Wait for message to appear (up to 5 seconds, polling every 100ms)
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> {
            Message msg = subscriber.nextMessage(Duration.ofMillis(100));
            assertThat(msg).isNotNull();
            assertThat(new String(msg.getData())).isEqualTo("hello world");
        });
}
```

**Example (WRONG)**:
```java
@Test
void testMessageAppearsOnBroker() throws Exception {
    publisher.publish("hello world");

    Thread.sleep(2000);  // ❌ Hard to debug; slow tests; unreliable
    Message msg = subscriber.nextMessage(Duration.ofSeconds(5));
    assertTrue(msg != null);
}
```

### Maven Dependency Setup

Add Awaitility to `integration-tests/pom.xml`:
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.1.1</version>
    <scope>test</scope>
</dependency>
```

---

## Active Technologies
- Java 21 (enforced per Principle IV)
- Quarkus 3.27.0 (LTS)
- Jackson 2.x (for JSON serialization with Quarkus integration)
- NATS JetStream 2.23.0 (messaging)
- CloudEvents 1.0 spec (with auto-generated metadata headers)

## Recent Changes
- 001-basic-publisher-api: Added Java 21 (enforced per Principle IV)
- 002-typed-publisher: Added typed publisher with JSON serialization and CloudEvents support
  - Key Classes: `TypedPayloadEncoder`, `CloudEventsHeaders`, `NatsPublisher<T>`, `SerializationException`
  - Features: Type-safe publishing, automatic encoding selection, CloudEvents metadata headers with auto-generation
