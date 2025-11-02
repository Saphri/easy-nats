# CDI Producer Contract: NATS Options Bean

**Feature**: 016-custom-nats-options
**Date**: 2025-11-02

## Contract Definition

### Produced Bean

```java
io.nats.client.Options
```

### Producer Signature

```java
@Produces
@DefaultBean
io.nats.client.Options natsOptions(EasyNatsConfig config)
```

### Location

- **Module**: `runtime`
- **Package**: `org.mjelle.quarkus.easynats.core`
- **Class**: `NatsConnectionProducer`

### Scope

- **Scope**: `@ApplicationScoped` (singleton per Quarkus application)
- **Lifecycle**: Created at application startup, destroyed at application shutdown

## Contract Behavior

### Default Production (No Custom Bean)

1. **Read Configuration**:
   - Extract `quarkus.easynats.servers` from Quarkus config
   - Extract `quarkus.easynats.username` from Quarkus config
   - Extract `quarkus.easynats.password` from Quarkus config
   - Extract `quarkus.easynats.ssl-enabled` from Quarkus config (default: false)

2. **Validate Configuration**:
   - `servers`: Must not be null or empty
   - `username`: Must not be null
   - `password`: Must not be null
   - Throw `ConfigException` with clear message if validation fails

3. **Create Options**:
   - Use `io.nats.client.Options.Builder()` to construct Options
   - Set servers from configuration
   - Set user info (username, password) from configuration
   - Set SSL enabled if configured
   - Call `build()` to create immutable Options instance

4. **Return Options**:
   - Return the constructed Options instance
   - Never return null

### Custom Production (Developer Provides Bean)

1. **CDI Resolution**:
   - Developer creates a bean producing `io.nats.client.Options` (unqualified)
   - CDI bean resolution: Developer's bean **overrides** the `@DefaultBean`
   - NatsConnection receives the custom Options

2. **Contract Responsibility**:
   - Extension does NOT validate custom bean contents
   - Developer is responsible for valid Options configuration
   - If custom bean throws exception during instantiation, CDI fails at startup

## Injection Contracts

### By NatsConnection

```java
@ApplicationScoped
public class NatsConnection {
    private final io.nats.client.Options options;

    @Inject
    public NatsConnection(io.nats.client.Options options) {
        this.options = options;
        // Use options to establish NATS connection
    }
}
```

**Expected Behavior**:
- Injection point receives either:
  - Default Options (from `NatsConnectionProducer`)
  - Custom Options (from developer-provided bean)
- Options must be non-null
- Options must be valid (NATS connection can be established)

### By User Code

Users can optionally inject Options in their application code:

```java
@ApplicationScoped
public class MyService {
    @Inject
    io.nats.client.Options options;  // Can inspect configuration if needed
}
```

**Expected Behavior**:
- Injection succeeds (bean exists)
- Returned Options is the same instance used by NatsConnection

## Error Handling Contracts

### Configuration Errors (Default Path)

**Scenario**: Required property missing in default producer

```
quarkus.easynats.servers=
quarkus.easynats.username=
quarkus.easynats.password=
```

**Expected Behavior**:
- Producer throws exception with message: "quarkus.easynats.servers is required"
- Application startup fails
- Error message is clear and actionable

**Responsibility**: Extension (NatsConnectionProducer validation)

### Custom Bean Configuration Errors

**Scenario**: Developer provides custom Options bean that is invalid

```java
@Produces
@Unremovable
public Options customOptions() {
    return new Options.Builder()
        // .servers() missing - INVALID!
        .userInfo("user", "pass")
        .build();
}
```

**Expected Behavior**:
- NATS client throws exception during connection establishment
- Application startup fails
- Exception message from NATS client library (may be cryptic)

**Responsibility**: Developer (custom bean must be valid)
**Note**: Extension does NOT validate custom bean content

### Multiple Beans Error

**Scenario**: Developer accidentally creates two Options beans

```java
@Produces
public Options bean1() { ... }

@Produces
public Options bean2() { ... }
```

**Expected Behavior**:
- CDI throws `javax.enterprise.inject.AmbiguousResolutionException`
- Error message: "Multiple beans found for type Options"
- Application startup fails

**Responsibility**: CDI (not extension)

### Bean Instantiation Error

**Scenario**: Custom bean throws exception during construction

```java
@Produces
public Options customOptions() {
    throw new RuntimeException("Invalid configuration");
}
```

**Expected Behavior**:
- CDI propagates exception during startup
- Application fails to start
- Stack trace shows the source of the error

**Responsibility**: CDI (not extension)

## Type Safety Contracts

### Producer Type

```java
io.nats.client.Options  // Exact type, no subclasses
```

- Producer returns `io.nats.client.Options` (from `io.nats:jnats`)
- NOT a subclass or wrapper
- Immutable (per NATS library guarantee)

### Bean Scope

```java
@ApplicationScoped  // Singleton per application
```

- Exactly one instance per Quarkus application
- Shared across all injection points
- Lifecycle tied to Quarkus application (startup/shutdown)

## State Contracts

### Immutability

- Options bean is **immutable** once created
- No methods modify the Options state
- Safe for concurrent access

### Lifecycle States

```
[Not Created]
       ↓
  CDI Bean Resolution
       ↓
  [Application Scoped - singleton]
       ↓
  NatsConnection initialization
       ↓
  [In Use]
       ↓
  Application Shutdown
       ↓
  [Destroyed]
```

## Testing Contracts

### Unit Test of Producer

```java
class NatsConnectionProducerTest {
    @Test
    void testProducerCreatesValidOptions() {
        EasyNatsConfig config = new EasyNatsConfig(
            "nats://localhost:4222",
            "admin",
            "secret",
            false
        );
        NatsConnectionProducer producer = new NatsConnectionProducer();
        Options options = producer.natsOptions(config);

        assertThat(options).isNotNull();
        // Verify Options contains expected configuration
    }

    @Test
    void testProducerValidatesRequiredProperties() {
        EasyNatsConfig config = new EasyNatsConfig(
            "", // empty servers
            "admin",
            "secret",
            false
        );
        NatsConnectionProducer producer = new NatsConnectionProducer();

        assertThatThrownBy(() -> producer.natsOptions(config))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("servers");
    }
}
```

### Integration Test of Default Bean

```java
@QuarkusTest
class CustomOptionsIntegrationTest {
    @Test
    void testDefaultOptionsFromProperties() {
        // application.properties provides required settings
        NatsConnection connection = CDI.current()
            .select(NatsConnection.class).get();

        assertThat(connection).isNotNull();
        // Connection was initialized with default Options
    }
}
```

### Integration Test of Custom Bean

```java
@QuarkusTest
class CustomOptionsOverrideTest {
    @Produces
    @Unremovable
    io.nats.client.Options customOptions() {
        return new Options.Builder()
            .servers(new String[]{"nats://localhost:4222"})
            .userInfo("test", "test")
            .build();
    }

    @Test
    void testCustomOptionsOverridesDefault() {
        NatsConnection connection = CDI.current()
            .select(NatsConnection.class).get();

        assertThat(connection).isNotNull();
        // Connection was initialized with custom Options
    }
}
```

## Important Caveats for Custom Beans

### 1. Complete Configuration Responsibility

When a custom `Options` bean is provided:
- **NatsConfiguration properties are completely ignored**
- The extension does NOT fall back to properties if custom bean is incomplete
- Developer must provide ALL required Options configuration
- No merging or partial overrides are possible

**Example of WRONG usage**:
```java
@Produces
@Unremovable
public Options customOptions(NatsConfiguration config) {
    // WRONG: Trying to inherit from config
    return new Options.Builder()
        .servers(new String[]{"my-server"})
        .userInfo(config.username().orElse("user"),
                  config.password().orElse("pass"))  // Config is IGNORED
        .build();
}
```

### 2. @Unremovable Annotation is Mandatory

Failure to use `@Unremovable` will result in:
- Bean optimized away by Quarkus during build
- Startup error: "Options bean not found during injection"
- Difficult to debug (error occurs at runtime, not compile time)

**Always use**:
```java
@Produces
@Unremovable  // DO NOT FORGET THIS
public Options customOptions() { ... }
```

### 3. No Extension Validation of Custom Beans

The extension does NOT validate:
- Whether servers are reachable
- Whether credentials are correct
- Whether Options configuration is complete
- Whether Options configuration is sensible

**Validation is delegated to NATS client library at connection time.**

### 4. Single Unqualified Bean Rule

- **Only ONE unqualified `Options` bean is allowed**
- Multiple beans cause `AmbiguousResolutionException`
- CDI enforces this, not the extension
- Using `@Named` qualifiers creates multiple named beans (not recommended)

### 5. Fail-Fast Approach

When custom bean is provided:
- If bean throws exception during instantiation: startup fails immediately
- If bean produces invalid Options: startup fails when connection is attempted
- **No silent fallback to defaults**
- **No partial initialization**

### 6. Testing Responsibility

When using custom `Options` bean:
- Tests MUST also provide the custom bean
- Cannot rely on application.properties in tests
- Test bean should use test-specific configuration (test broker, test credentials)
- **Test configuration must NOT use production values**

### 7. NatsConfiguration Bypass

Custom bean **completely and permanently bypasses** NatsConfiguration:
- All properties are ignored (servers, username, password, ssl-enabled, tls-configuration-name, log-payloads-on-error, etc.)
- Extension does NOT read or validate NatsConfiguration when custom bean exists
- No exceptions or warnings if contradictory configs exist (properties are simply ignored)

### 8. Documentation Responsibility

When using custom Options:
- Document WHY custom bean is needed (what constraints/requirements)
- Document how to update Options when deployment changes
- Document any performance tuning settings and their implications
- Include NATS Options API documentation link for maintainers

## References

- [NATS Client Library - Options](https://javadoc.io/doc/io.nats/jnats)
- [NATS Client Library - Options Javadoc](https://javadoc.io/doc/io.nats/jnats/latest/io/nats/client/Options.html)
- [Jakarta CDI Specification - Bean Definition](https://jakarta.ee/specifications/cdi/)
- [Jakarta CDI Specification - Unremovable Beans](https://jakarta.ee/specifications/cdi/)
- [Quarkus CDI Guide](https://quarkus.io/guides/cdi)
- [Quarkus Arc - Bean Discovery and Resolution](https://quarkus.io/guides/cdi-reference#arc)
