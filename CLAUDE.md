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

#### Integration Test Structure and Conventions

This project uses a **two-tier test structure** to enable both fast JVM testing and production-ready native image validation:

**Test Naming and Annotations:**
- **`*Test` classes** (e.g., `CloudEventTest.java`, `ValidationTest.java`):
  - Use `@QuarkusTest` annotation
  - Run on JVM (Surefire plugin, fast feedback during development)
  - Located in `integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/`
  - Contains all test methods (assertions, setup, teardown)

- **`*IT` classes** (e.g., `CloudEventIT.java`):
  - Use `@QuarkusIntegrationTest` annotation
  - Run as native image tests (Failsafe plugin, validates production readiness)
  - Located in same package as `*Test` classes
  - **Extend their corresponding `*Test` class** to reuse all test methods
  - Minimal boilerplate (typically just class declaration and inheritance)
  - Not all test classes have an `*IT` pair (e.g., `ValidationTest` runs on JVM only)

**Example Pattern** (from `CloudEventTest.java` and `CloudEventIT.java`):

The key principle: **Never use `@Inject` field injection in base test classes**. Instead:
- Use RestAssured to call REST endpoints (the extension's functionality)
- Use static utility methods to access NATS connections


```java
// CloudEventTest.java - Contains actual test methods with @QuarkusTest
@QuarkusTest
@DisplayName("CloudEvent Binary-Mode Integration Tests")
class CloudEventTest {

    @Test
    @DisplayName("Valid CloudEvent binary-mode with POJO data is unwrapped and deserialized")
    void testValidCloudEventBinaryMode() {
        // Given
        OrderData orderData = new OrderData("ORD-001", "CUST-001", 150.00);

        // When - Publish CloudEvent with REST endpoint
        given()
            .contentType(ContentType.JSON)
            .body(orderData)
            .when()
            .post("/publish/order")
            .then()
            .statusCode(204);

        // Then - Verify order was received and deserialized by subscriber
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                OrderData result = given()
                    .when()
                    .get("/subscribe/last-order")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(OrderData.class);

                assertThat(result).isEqualTo(orderData);
            });
    }
}

// CloudEventIT.java - Reuses all test methods in native image context
@QuarkusIntegrationTest
public class CloudEventIT extends CloudEventTest {
    // Inherits all test methods from CloudEventTest
    // ✓ Works because base class has no @Inject fields
}
```

**Package Structure:**
```
integration-tests/
├── src/test/java/org/mjelle/quarkus/easynats/it/
│   ├── CloudEventTest.java           (JVM tests with @QuarkusTest)
│   ├── CloudEventIT.java             (Native tests with @QuarkusIntegrationTest)
│   ├── AnnotationContractTest.java   (JVM tests only)
│   ├── ValidationTest.java           (JVM tests only)
│   ├── StartupValidationTest.java    (JVM tests only)
│   ├── NatsStreamTestResource.java   (Quarkus test resource for NATS setup)
│   └── NatsTestUtils.java            (Static utilities for accessing NATS connections)
└── src/main/java/org/mjelle/quarkus/easynats/it/
    ├── OrderListener.java             (Subscriber bean with @NatsSubscriber method)
    ├── OrderPublisherResource.java    (REST endpoint for publishing)
    ├── OrderSubscriberResource.java   (REST endpoint for retrieving received messages)
    ├── model/
    │   └── OrderData.java             (Record type for testing typed subscribers)
    └── example/
        ├── GreetingListener.java      (Example subscriber bean)
        └── GreetingResource.java      (Example REST endpoints)
```

**Running Tests:**
```bash
# Run all tests (JVM only, no native compilation)
./mvnw clean test

# Run tests for integration-tests module (JVM only)
./mvnw -pl integration-tests clean test

# Run JVM + native integration tests (requires GraalVM)
./mvnw clean install -Pit

# Run a single test class (JVM)
./mvnw test -Dtest=CloudEventTest

# Run specific test method (JVM)
./mvnw test -Dtest=CloudEventTest#testValidCloudEventBinaryMode

# Run tests excluding native (*IT) tests
./mvnw test -pl integration-tests
```

**Dependencies:**
Integration tests require NATS JetStream running. The project uses Docker Compose for automated NATS setup (see `docker-compose-devservices.yml`). This is automatically started by Quarkus when running tests with the `@QuarkusTest` or `@QuarkusIntegrationTest` annotations.

**Rationale:**
- **JVM tests** (`*Test` with `@QuarkusTest`): Fast feedback loop during development (seconds vs minutes)
- **Native IT tests** (`*IT` with `@QuarkusIntegrationTest`): Validates the extension works correctly in native image mode (catches native-specific issues early)
- **Code reuse**: Single set of test methods validated in both JVM and native contexts, ensuring consistency

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

### Integration Test Dependency Injection (CRITICAL)

- **NEVER use `@Inject` field injection in integration test classes**: This breaks `@QuarkusIntegrationTest` inheritance
- **Why it breaks**: `@QuarkusIntegrationTest` doesn't support field injection the same way as `@QuarkusTest`; when `*IT` extends `*Test`, injected fields are not properly initialized in the native context
- **Workaround**: Access beans through REST endpoints using RestAssured, or use static utility methods
- **Rationale**: Keeps integration tests compatible with both JVM and native image testing

**Example (WRONG - NEVER DO THIS)**:
```java
@QuarkusTest
public class PublisherTest {
    @Inject NatsPublisher publisher;  // ❌ BREAKS @QuarkusIntegrationTest inheritance

    @Test
    void testPublisher() throws Exception {
        publisher.publish("test.subject", "hello");
    }
}

@QuarkusIntegrationTest
public class PublisherIT extends PublisherTest {
    // ❌ publisher field won't be initialized properly in native context
}
```

**Example (CORRECT)**:
```java
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
public class PublisherTest {
    // ✓ No @Inject fields

    @Test
    void testPublisher() throws Exception {
        // Access bean through REST endpoint
        given()
            .queryParam("subject", "test.subject")
            .queryParam("message", "hello")
            .when()
            .get("/publish/message")
            .then()
            .statusCode(204);
    }
}

@QuarkusIntegrationTest
public class PublisherIT extends PublisherTest {
    // ✓ Works - no injected fields to inherit
}
```

---

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

### Custom Exceptions

- **Define Custom Exceptions for Domain Errors**: Create specific exception classes for domain-specific errors
- **PublishingException Example**: Custom exception for NATS publishing failures that wraps lower-level NATS exceptions
- **Rationale**: Provides cleaner API boundaries and allows callers to handle domain errors specifically

**Example (CORRECT)**:
```java
public class PublishingException extends Exception {
    public PublishingException(String message) {
        super(message);
    }

    public PublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}

// In publish method:
try {
    jetStream.publish(subject, headers, payload);
} catch (IOException | JetStreamApiException e) {
    throw new PublishingException("Failed to publish message", e);
}
```

### Configuration Management

- **Use MicroProfile Config API**: Access Quarkus configuration via `org.eclipse.microprofile.config.ConfigProvider`
- **Graceful Fallbacks**: Always provide sensible fallbacks for optional configuration
- **Try-Catch for Config Access**: Wrap config access in try-catch since it may not be available at all times

**Example (CORRECT)**:
```java
String appName = ConfigProvider.getConfig()
    .getOptionalValue("quarkus.application.name", String.class)
    .orElse(null);
if (appName != null && !appName.isEmpty()) {
    return appName;
}
// Fall back to hostname or other default
```

---

## Active Technologies
- Java 21 (enforced per Principle IV) (001-basic-publisher-api)
- Quarkus 3.27.0 LTS (extension framework)
- CloudEvents 1.0 (transparent event format) (005-transparent-cloudevents)
- NATS JetStream (messaging broker)
- Jackson Databind (serialization)
- Java 21 (enforced per Constitution Principle IV) (006-typed-subscriber)
- N/A (messaging system, no data storage) (006-typed-subscriber)
- Java 21 (enforced per Constitution Principle IV) + Jackson Databind (JSON serialization), NATS JetStream client, Quarkus Arc (CDI) (007-typed-serialization)
- N/A (messaging system) (007-typed-serialization)
- Java 21 (enforced per Constitution IV) + Quarkus 3.27.0, NATS JetStream client (io.nats:jnats), Jackson Databind (inherited from 007-typed-serialization) (001-durable-nats-consumers)
- N/A (messages managed by NATS JetStream) (001-durable-nats-consumers)
- Java 21 (enforced per Constitution Principle IV) + Quarkus 3.27.0 LTS, NATS JetStream client (io.nats:jnats) (009-explicit-ack-nak)
- N/A (messaging system; messages managed by NATS JetStream) (009-explicit-ack-nak)

## Recent Changes
- 005-transparent-cloudevents: Implemented transparent CloudEvent wrapping in NatsPublisher with custom PublishingException
- 002-typed-publisher: Implemented generic typed publisher with CloudEvents and REST improvements
- 001-basic-publisher-api: Added Java 21 (enforced per Principle IV)
