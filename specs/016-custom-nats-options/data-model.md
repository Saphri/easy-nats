# Data Model: Custom NATS Options via CDI

**Feature**: 016-custom-nats-options
**Date**: 2025-11-02

## Core Entity: Options Bean

### Definition

The `io.nats.client.Options` bean is a CDI-managed dependency that configures NATS connection behavior. It is immutable and created once during application startup.

### Lifecycle

1. **Creation**:
   - **Default Path**: `@DefaultBean` CDI producer reads `NatsConfiguration` properties and creates Options
   - **Custom Path**: Developer provides unqualified `Options` bean; CDI injects the custom bean (overrides default)
   - **Key Point**: When custom bean is provided, `NatsConfiguration` is completely bypassed

2. **Injection**:
   - NatsConnection initialization code injects the Options bean via constructor
   - Injection point: `@Inject io.nats.client.Options options`
   - CDI resolution: Standard CDI bean resolution (default or custom)
   - Result: Either default Options (from properties) OR custom Options (from developer bean)

3. **Usage**:
   - Passed to `NatsConnection` constructor to establish NATS connection
   - Immutable; never modified after creation
   - NatsConfiguration is never accessed after Options bean is injected

### Attributes (from NATS Client Library)

The `io.nats.client.Options` class (from `io.nats:jnats`) encapsulates connection configuration:

- **Servers**: List of NATS broker addresses
  - Type: `java.util.List<String>`
  - Source: `quarkus.easynats.servers` property or custom bean
  - Immutable after creation

- **Authentication**: Username and password
  - Type: `String`
  - Source: `quarkus.easynats.username`, `quarkus.easynats.password`
  - Immutable after creation

- **SSL/TLS**: Enable encrypted connection
  - Type: `boolean`
  - Source: `quarkus.easynats.ssl-enabled` property
  - Default: `false`

- **Connection Timeouts**: Configurable via Options builder
  - Type: Various (Duration, milliseconds)
  - Source: Custom bean (if developer needs custom timeouts)

- **Retry Policies**: Automatic reconnection and retry behavior
  - Type: Various Options builder methods
  - Source: Custom bean (if developer needs custom retry logic)

### Validation Rules

1. **Default Producer**:
   - Required properties (if no custom bean provided):
     - `quarkus.easynats.servers` must not be empty
     - `quarkus.easynats.username` must not be null
     - `quarkus.easynats.password` must not be null
   - Invalid property values → application startup fails with clear error message

2. **Custom Bean**:
   - Must be a valid CDI bean producing `io.nats.client.Options`
   - Must not be null (CDI enforces via `@Inject`)
   - Instantiation errors → CDI fails with stack trace at startup

3. **CDI Resolution**:
   - Multiple unqualified `Options` beans → CDI throws `AmbiguousResolutionException`
   - Extension does NOT validate; CDI validation occurs at startup

## Configuration Entity: EasyNatsConfig

### Definition

Configuration properties bound to `quarkus.easynats.*` namespace via Quarkus ConfigProvider.

### Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `quarkus.easynats.servers` | String | Yes | N/A | Comma-separated list of NATS server URLs (e.g., `nats://localhost:4222`) |
| `quarkus.easynats.username` | String | Yes | N/A | Authentication username for NATS broker |
| `quarkus.easynats.password` | String | Yes | N/A | Authentication password for NATS broker |
| `quarkus.easynats.ssl-enabled` | Boolean | No | `false` | Enable SSL/TLS for broker connection |

### Validation

- Missing required properties → application startup fails (fail-fast approach per Constitution Principle II)
- Invalid server URL format → application startup fails with validation error
- Empty servers list → application startup fails

## Relationships

```
┌─────────────────────────────────────────────────────────────┐
│ Quarkus Application Startup                                 │
└─────────────────────────────────────────────────────────────┘

TWO POSSIBLE PATHS (mutually exclusive):

PATH 1: DEFAULT (no custom Options bean)
────────────────────────────────────────
NatsConfiguration (properties: servers, username, password, ssl-enabled, ...)
       ↓
NatsConnectionProducer.natsOptions(@DefaultBean)
       ↓
io.nats.client.Options (default)
       ↓
NatsConnection (constructor injects Options)
       ↓
NATS JetStream Connection established

PATH 2: CUSTOM (developer provides Options bean)
─────────────────────────────────────────────────
Developer's Custom Options Bean (e.g., CustomNatsOptionsProvider)
       ↓
io.nats.client.Options (custom)
       ↓
NatsConnection (constructor injects Options)
       ↓
NATS JetStream Connection established

KEY: NatsConfiguration is ONLY used in PATH 1 (default producer)
     In PATH 2, NatsConfiguration properties are completely ignored
```

## State Transitions

The Options bean is **stateless and immutable** once created:

```
[Application Startup]
          ↓
    CDI Bean Resolution
          ↓
    ├─→ Default Bean (from properties)
    │        OR
    └─→ Custom Bean (developer-provided)
          ↓
    NatsConnection initialization
          ↓
    [Application Ready]
```

## Edge Cases & Constraints

1. **Null Options**: CDI prevents null injection; fails at startup
2. **Bean Instantiation Error**: Custom bean throws exception → CDI startup failure
3. **Missing Properties**: Default producer reads from config; validation fails at startup
4. **JetStream Disabled**: If developer's custom Options disables JetStream (if possible), feature still works; user responsibility
5. **Multiple Beans**: CDI throws `AmbiguousResolutionException`; not handled by extension

## Notes

- The `io.nats.client.Options` class is final and immutable (from NATS library)
- No extension-level validation of Options content (delegated to NATS client)
- Custom beans provide full flexibility; developers have complete control
- Default producer uses `@DefaultBean` to allow overrides without conflicts
