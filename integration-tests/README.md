# Integration Tests

This module contains the integration tests for the Quarkus EasyNATS extension. It uses a **two-tier testing approach**: JVM tests (`*Test.java`) for fast feedback and native image tests (`*IT.java`) for production-ready validation.

## Running the Tests

```bash
# Run JVM tests only (fast feedback during development)
./mvnw clean test -pl integration-tests

# Run JVM + native image tests (requires GraalVM, slower)
./mvnw clean install -Pit

# Run specific test class
./mvnw test -Dtest=CloudEventTest
```

## Test Organization

### Test Classes

| Test Class | Scope | Focus Area |
|------------|-------|-----------|
| `CloudEventTest` | JVM + Native | CloudEvents binary-mode unwrapping and typed deserialization |
| `CloudEventIT` | Native only | Native image compatibility of CloudEvent handling |
| `ValidationTest` | JVM only | @NatsSubscriber annotation validation |
| `AnnotationContractTest` | JVM only | Subscriber method signature contracts |
| `StartupValidationTest` | JVM only | Application startup validation |

### Test Infrastructure

- **`OrderListener`**: Subscriber bean that receives typed `OrderData` messages
- **`OrderPublisherResource`**: REST endpoint for publishing messages to `/publish/order`
- **`OrderSubscriberResource`**: REST endpoint for retrieving received messages from `/subscribe/last-order`
- **`GreetingListener` & `GreetingResource`**: Example subscriber and publisher for simple String messages
- **`NatsStreamTestResource`**: Quarkus test resource that sets up JetStream test streams
- **`NatsTestUtils`**: Static utilities for accessing NATS connections in tests

## Test Scenarios

This module tests the following functionality:

- ✅ Typed subscriber message reception (POJOs, Java 21 Records, generic types)
- ✅ CloudEvents 1.0 binary-mode format unwrapping and validation
- ✅ Automatic Jackson deserialization to target types
- ✅ Error handling for invalid CloudEvents and deserialization failures
- ✅ Subscriber bean lifecycle and CDI dependency injection
- ✅ Build-time discovery and validation of @NatsSubscriber methods
- ✅ Native image compatibility (via native tests)
