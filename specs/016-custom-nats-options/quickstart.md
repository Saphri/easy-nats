# Quick Start: Custom NATS Options via CDI

**Feature**: 016-custom-nats-options
**Date**: 2025-11-02

This guide shows how to use the Quarkus Easy NATS extension with default Options (from properties) and custom Options (developer-provided).

## Pattern 1: Using Default Options (from Properties)

### Setup

Configure `application.properties`:

```properties
quarkus.easynats.servers=nats://localhost:4222
quarkus.easynats.username=admin
quarkus.easynats.password=secret
quarkus.easynats.ssl-enabled=false
```

Or `application.yaml`:

```yaml
quarkus:
  easynats:
    servers: nats://localhost:4222
    username: admin
    password: secret
    ssl-enabled: false
```

### How It Works

1. The Quarkus Easy NATS extension provides a `@DefaultBean` CDI producer
2. The producer reads `quarkus.easynats.*` properties
3. The producer creates an `io.nats.client.Options` instance with those settings
4. The NatsConnection uses the injected Options to establish the NATS connection
5. Application starts with the configured connection

### Code Example

```java
@ApplicationScoped
public class OrderService {
    @Inject
    NatsConnection connection;  // Uses default Options from properties

    public void publishOrder(Order order) throws Exception {
        JetStream js = connection.getJetStream();
        js.publish("orders", order.toJson().getBytes());
    }
}
```

## Pattern 2: Providing Custom Options (Advanced)

### Scenario

You need full control over NATS connection behavior beyond what properties provide:
- Custom connection timeout and retry policies
- Advanced SSL/TLS configuration (client certificates, custom truststore)
- Custom authentication handler (e.g., callback-based, token rotation)
- Connection pool settings and buffer sizes
- Specific error handling policies
- Custom connection listener for lifecycle events

### When to Use Custom Options

Use this pattern when:
- ✅ Your deployment requires non-standard connection behavior
- ✅ You need dynamic configuration (e.g., credentials from a secure vault)
- ✅ You must integrate with enterprise infrastructure (proxies, load balancers, etc.)
- ✅ You require performance tuning specific to your deployment

**Do NOT use this pattern when:**
- ❌ Simple property-based configuration would suffice (use Pattern 1)
- ❌ You're just overriding a few values (consider extending the extension instead)
- ❌ You don't fully understand the NATS Options API

### Implementation

Create a CDI bean that produces `io.nats.client.Options`:

```java
import io.nats.client.Options;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;

@ApplicationScoped
public class CustomNatsOptionsProvider {

    @Produces
    @Unremovable  // CRITICAL: Prevent Quarkus from optimizing away this bean
    public Options customNatsOptions() {
        return new Options.Builder()
            // Server configuration - YOU are responsible for this
            .servers(new String[]{"nats://prod-nats-1:4222", "nats://prod-nats-2:4222"})

            // Authentication - YOU are responsible for this
            .userInfo("app-user", "secure-password")

            // Connection tuning
            .connectionTimeout(Duration.ofSeconds(10))
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(60)

            // SSL/TLS configuration
            .secure()  // Enable TLS
            // Optionally: .tlsStrategy(createCustomSSLContext())

            // Additional options as needed
            .pedantic(false)
            .noHeaders(false)
            // ... more options

            .build();
    }
}
```

### How It Works

1. Your custom bean produces an `io.nats.client.Options` instance
2. CDI bean resolution: Your unqualified bean **completely overrides** the default `@DefaultBean`
3. NatsConnection receives your custom Options and uses it
4. **NatsConfiguration properties are IGNORED** — your bean has full responsibility

### Code Example (Using Custom Options)

```java
@ApplicationScoped
public class OrderService {
    @Inject
    NatsConnection connection;  // Uses custom Options from CustomNatsOptionsProvider

    public void publishOrder(Order order) throws Exception {
        JetStream js = connection.getJetStream();
        js.publish("orders", order.toJson().getBytes());
        // Connection uses YOUR custom timeout, retry logic, TLS config, etc.
    }
}
```

### ⚠️ CRITICAL CAVEATS and Gotchas

**1. You Are Responsible for Complete Configuration**
```
When you provide a custom Options bean:
✗ quarkus.easynats.servers → IGNORED
✗ quarkus.easynats.username → IGNORED
✗ quarkus.easynats.password → IGNORED
✗ quarkus.easynats.ssl-enabled → IGNORED
✗ quarkus.easynats.tls-configuration-name → IGNORED
✗ All other quarkus.easynats.* properties → IGNORED

YOUR Options.Builder MUST provide:
✓ servers (at least one)
✓ authentication (if required)
✓ SSL/TLS settings (if needed)
✓ All other connection configuration
```

**2. @Unremovable Annotation is MANDATORY**
```
WRONG - Will be optimized away and fail at runtime:
@Produces
public Options customOptions() { ... }

CORRECT - Prevents Quarkus optimization:
@Produces
@Unremovable
public Options customOptions() { ... }

ERROR if missing:
  Exception during startup: "Options bean not found during injection"
```

**3. Single Bean Rule (Enforced by CDI)**
```
Multiple unqualified Options beans WILL fail:

@Produces  ← Bean 1
public Options options1() { ... }

@Produces  ← Bean 2 - CONFLICT!
public Options options2() { ... }

ERROR: javax.enterprise.inject.AmbiguousResolutionException
  "Multiple beans found for type Options"

SOLUTION: Delete the duplicate bean
```

**4. No Partial Configuration**
```
You CANNOT partially override defaults:

WRONG - Trying to inherit some defaults:
@Produces
@Unremovable
public Options customOptions(NatsConfiguration config) {
    return new Options.Builder()
        .servers(new String[]{"custom-server"})
        .userInfo(config.username().orElse(""),
                  config.password().orElse(""))  ← Config is IGNORED
        .build();
}

NatsConfiguration is completely bypassed when custom bean exists.
You must hardcode or provide all values yourself.
```

**5. Environment-Specific Configuration**
```
For different environments, you have options:

Option A: Use Quarkus Profiles
@ApplicationScoped
@IfBuildProperty(name = "deployment", value = "prod")
public class ProdNatsOptions {
    @Produces
    @Unremovable
    public Options customNatsOptions() {
        return new Options.Builder()
            .servers(new String[]{"prod-nats:4222"})
            // ...
            .build();
    }
}

Option B: Read from external source (not from quarkus.easynats.*)
@ApplicationScoped
public class DynamicNatsOptions {
    @Inject
    VaultService vault;  // Your secure config source

    @Produces
    @Unremovable
    public Options customNatsOptions() {
        String servers = vault.getSecret("nats.servers");
        String username = vault.getSecret("nats.username");
        // ...
        return new Options.Builder()
            .servers(servers.split(","))
            .userInfo(username, password)
            .build();
    }
}
```

**6. Testing with Custom Options**
```
In your tests, you must also provide the custom Options bean:

WRONG - Using default in test, custom in production:
// Test only has application.properties with default config
@QuarkusTest
class MyServiceTest { ... }  // Uses default Options,
                             // but prod uses custom Options
                             // TEST != PROD

CORRECT - Test uses same custom Options as production:
@QuarkusTest
class MyServiceTest {
    @Produces
    @Unremovable
    Options testOptions() {
        return new Options.Builder()
            .servers(new String[]{"localhost:4222"})
            .userInfo("test", "test")
            .build();
    }

    @Test
    void testPublisher() { ... }  // Uses same Options as prod
}
```

**7. Performance Implications**
```
Custom Options allows you to optimize for your specific deployment:

Good Practices:
✓ Tune connectionTimeout based on network latency
✓ Set maxReconnects and reconnectWait based on deployment stability
✓ Configure buffer sizes based on message throughput
✓ Use connection listeners to monitor health

Pitfalls:
✗ Copy-pasting Options from another project (different needs)
✗ Using overly aggressive timeouts (may disconnect prematurely)
✗ Setting unlimited reconnects (may mask real problems)
✗ Not monitoring Options behavior in production
```

### Important Notes

- **Full responsibility**: Your bean takes 100% ownership of connection configuration
  - NatsConfiguration properties are completely bypassed
  - No fallback or merging of settings
  - You must provide sensible defaults for all required Options

- **Single bean rule**: Only one unqualified `Options` bean is allowed
  - If you accidentally create two, CDI will fail at startup
  - Use `@Unremovable` annotation to prevent Quarkus from optimizing away your bean
  - Use `@Named` qualifiers ONLY if you need multiple beans (advanced use case)

- **Full control comes with responsibility**: Custom Options bean can configure ANY aspect
  - Timeouts, retries, SSL/TLS, authentication, pool sizes, listeners, etc.
  - You must understand the implications of each setting
  - Refer to `io.nats.client.Options` Javadoc carefully

- **No fallback to defaults**: If your Options bean throws an exception or is invalid
  - Application will fail to start (fail-fast approach)
  - No silent fallback to NatsConfiguration properties
  - Check logs carefully during development

## Comparison

| Aspect | Default Pattern | Custom Pattern |
|--------|-----------------|----------------|
| Configuration | `quarkus.easynats.*` properties | Hardcoded in Java code |
| Flexibility | Limited to predefined properties | Full NATS Options API |
| Use Case | Simple deployments, environment-specific config | Advanced scenarios needing custom behavior |
| Effort | Minimal (just set properties) | More code required |
| Testing | Easy (override properties in tests) | Requires test bean provider |

## Testing with Custom Options

In your integration tests, create a test-scoped Options provider:

```java
import io.quarkus.test.junit.QuarkusTest;
import io.nats.client.Options;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@QuarkusTest
class OrderServiceTest {

    @Produces
    @Singleton
    Options testNatsOptions() {
        // Test-specific configuration
        return new Options.Builder()
            .servers(new String[]{"nats://localhost:4222"})  // Test broker address
            .userInfo("test", "test")
            .build();
    }

    @Test
    void testPublishOrder() throws Exception {
        OrderService service = CDI.current().select(OrderService.class).get();
        service.publishOrder(new Order("ORD-001"));
        // Verify order published
    }
}
```

## Troubleshooting

### Application fails to start: "Multiple Options beans found"

**Cause**: You have multiple CDI beans producing `Options`

**Solution**:
- Check for duplicate `@Produces` methods in your beans
- Ensure only one bean produces unqualified `Options`
- Use `@Named` qualifier if you need multiple Options beans (advanced use case)

### Application fails to start: "Failed to read quarkus.easynats.servers property"

**Cause**: Default Options provider was used, but required properties are missing

**Solution**:
- If using default behavior: ensure `quarkus.easynats.servers`, `username`, and `password` are set
- If using custom Options: remove the required properties (custom bean takes precedence)

### Custom Options bean not being used

**Cause**: Your bean may have been optimized away by Quarkus

**Solution**:
- Add `@Unremovable` annotation to your `@Produces` method
- Ensure your bean is in a discoverable location (package scanned by CDI)

## References

- [NATS Client Options API](https://javadoc.io/doc/io.nats/jnats)
- [Quarkus CDI Documentation](https://quarkus.io/guides/cdi)
- [Quarkus Configuration Guide](https://quarkus.io/guides/config)
