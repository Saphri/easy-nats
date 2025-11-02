# Contract: NATS Dev Services Build Processor

**Feature**: 017-docker-compose-discovery
**Component**: `NatsDevServicesProcessor`
**Type**: Build-time processor (Quarkus extension framework)
**Scope**: Deployment module only (no runtime changes)

---

## Overview

This contract defines the public API and internal behavior of the modified `NatsDevServicesProcessor` that discovers running NATS containers from docker-compose instead of creating managed containers or reading configuration from application.properties.

---

## Primary Build Step

### `startNatsDevService()`

**Signature** (modified from current):

```java
@BuildStep
void startNatsDevService(
    LaunchModeBuildItem launchMode,
    DevServicesComposeProjectBuildItem composeProjectBuildItem,
    NatsDevServicesBuildTimeConfiguration config,
    DevServicesConfig devServicesConfig,
    List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
    BuildProducer<DevServicesResultBuildItem> devServicesResult
)
```

**Behavior**:

1. **Guard Condition**: Check if Dev Services are explicitly disabled
   - If `config.enabled() == false`: Log "NATS Dev Services disabled" and return
   - If `devServicesConfig.isEnabled() == false`: Return (framework-level disable)
   - If launch mode is not DEV/TEST: Return (only applicable in dev/test modes)

2. **Primary Path: Attempt Discovery**
   - Call `discoverNatsContainer(composeProject, launchMode, config)`
   - If container found: Proceed to step 3
   - If not found: Proceed to step 4
   - **REMOVED**: All container creation/startup logic from current implementation (lines 64-93 will be deleted)

3. **Success: Discovered Container**
   - Extract `ContainerConfig` from discovery result
   - Log at INFO level: "NATS container discovered at <host>:<port>, container ID: <id>"
   - Call `buildConfigProvider(containerConfig)` to map to Quarkus properties
   - Produce `DevServicesResultBuildItem.discovered()` with:
     - Name: "quarkus-easynats-devservices"
     - Container ID: <from container>
     - Configuration map: `containerConfig.toConfigurationMap()`

4. **Failure: No Container Discovered**
   - Log at WARN level: "NATS container not discovered. Ensure docker-compose NATS service is running and properly configured."
   - Do NOT create/start a managed container (per FR-009)
   - Do NOT fall back to application.properties (per FR-006)
   - Dev Services NOT initialized—application must provide explicit configuration
   - **REMOVED**: All fallback container creation logic

**Error Handling**:
- Container discovery throws exception: Catch, log error, fail-fast with descriptive message
- Configuration validation fails: Throw RuntimeException with clear guidance
- No fallback mechanisms—fail-fast philosophy per Constitution

---

## Internal Methods

### `discoverNatsContainer()`

**Purpose**: Discover running NATS containers from docker-compose project.

**Signature** (internal, new or significantly modified):

```java
private Optional<ContainerConfig> discoverNatsContainer(
    DevServicesComposeProjectBuildItem composeProjectBuildItem,
    LaunchMode launchMode,
    NatsDevServicesBuildTimeConfiguration config
)
```

**Returns**:
- `Optional<ContainerConfig>` with discovered container metadata if found
- `Optional.empty()` if no container discovered

**Algorithm**:

1. Determine NATS image pattern to search for
   - Default: "nats:*" (official NATS image)
   - Configurable: from `config.imageName()` if provided

2. Determine NATS client port (standard: 4222)
   - Use `config.port()` if provided
   - Otherwise default to 4222

3. Call Quarkus `ComposeLocator.locateContainer()`
   ```java
   Optional<ContainerAddress> discovered = ComposeLocator.locateContainer(
       composeProjectBuildItem,
       List.of(config.imageName()),
       port,
       launchMode,
       false  // do not start
   );
   ```

4. If found, extract credentials and build config:
   ```java
   return discovered.map(containerAddress -> {
       Map<String, String> env = containerAddress.getEnv(); // or query Docker API
       String username = env.getOrDefault("NATS_USERNAME", "nats");
       String password = env.getOrDefault("NATS_PASSWORD", "nats");
       boolean ssl = Boolean.parseBoolean(env.getOrDefault("NATS_TLS", "false"));

       return new ContainerConfig(
           containerAddress.getId(),
           containerAddress.getHost(),
           containerAddress.getPort(),
           username,
           password,
           ssl
       );
   });
   ```

5. If not found, return `Optional.empty()`

**Logging**:
- DEBUG: "Attempting to discover NATS container with image <pattern> on port <port>"
- INFO: "NATS container discovered: <containerId> at <host>:<port>"
- WARN: "NATS container not discovered after search attempt"

---

### `buildConfigProvider()`

**Purpose**: Build configuration mapping from discovered container.

**Signature** (existing, may be refactored):

```java
private Map<String, java.util.function.Function<Object, String>> buildConfigProvider(
    ContainerConfig config
)
```

**Returns**:
```java
Map.of(
    "quarkus.easynats.servers", cfg -> config.toConnectionUrl(),
    "quarkus.easynats.username", cfg -> config.username(),
    "quarkus.easynats.password", cfg -> config.password(),
    "quarkus.easynats.ssl-enabled", cfg -> String.valueOf(config.sslEnabled())
)
```

**Key Change**: Removed property-reading logic from this method.
- No longer reads from `NatsDevServicesBuildTimeConfiguration`
- Pure transformation from container metadata to property map

---

## Configuration Input

### `NatsDevServicesBuildTimeConfiguration`

**Current Usage** (to be partially deprecated in favor of docker-compose):

```java
public interface NatsDevServicesBuildTimeConfiguration {
    /**
     * Enable/disable NATS Dev Services
     * @return true if dev services enabled (default: true)
     */
    boolean enabled();

    /**
     * NATS Docker image name (for matching)
     * @return image pattern (default: "nats:latest")
     */
    String imageName();

    /**
     * NATS service name for shared network registration
     * @return service name (default: "nats")
     */
    String serviceName();

    /**
     * Explicit NATS port override
     * @return optional port (default: 4222)
     */
    Optional<Integer> port();

    // Deprecated for dev services (now only for explicit server configuration):
    // - username() [ignored in discovery path]
    // - password() [ignored in discovery path]
    // - sslEnabled() [ignored in discovery path]
}
```

**New Behavior**:
- `enabled()`: Controls whether to attempt discovery (still honored)
- `imageName()`: Specifies NATS image pattern for discovery search
- `serviceName()`: Used for shared network registration (unchanged)
- `port()`: Specifies NATS port if non-standard (unchanged)
- Other fields: Ignored in discovery flow (no longer used)

---

## Dependency Inputs

**Injected by Quarkus**:

- `LaunchModeBuildItem`: Current launch mode (DEV, TEST, NORMAL)
- `DevServicesComposeProjectBuildItem`: Docker compose project metadata
- `DevServicesConfig`: Framework-level dev services enable/disable
- `List<DevServicesSharedNetworkBuildItem>`: Shared network configuration
- `BuildProducer<DevServicesResultBuildItem>`: For producing dev service results

**No new dependencies required** (all existing Quarkus APIs).

---

## Output Contracts

### Successful Discovery

**Produced Item**: `DevServicesResultBuildItem.discovered()`

```
{
  name: "quarkus-easynats-devservices",
  containerId: "<docker-container-id>",
  config: {
    "quarkus.easynats.servers": "nats://host:port" or "tls://host:port",
    "quarkus.easynats.username": "extracted-username",
    "quarkus.easynats.password": "extracted-password",
    "quarkus.easynats.ssl-enabled": "true" or "false"
  }
}
```

**Side Effects**:
- INFO level log with discovery details
- Configuration properties automatically applied by Quarkus framework

---

### Discovery Failure

**Produced Item**: None (dev services not initialized)

**Side Effects**:
- WARN level log with helpful debugging information
- Application continues without dev services
- Application startup fails later if NATS connection not explicitly configured

---

## Error Scenarios

| Scenario | Trigger | Behavior | Log Level |
|----------|---------|----------|-----------|
| No container running | Docker query returns empty | Return empty Optional | WARN |
| Container found, env parse fails | Invalid env format | Log error, fail-fast | ERROR |
| Container port not accessible | Docker port mapping broken | Exception from Docker API | ERROR |
| Config validation fails | Invalid ContainerConfig created | Throw RuntimeException | ERROR |
| Dev Services disabled | `enabled()` returns false | Early return | INFO |
| Launch mode not dev/test | PROD mode active | Early return | DEBUG |

---

## State and Side Effects

**State**: No persistent state (build-time only)
- Discovery results not stored between builds
- Configuration ephemeral (applied to Quarkus properties)
- Container not created or managed by this processor

**Side Effects**:
- Docker API queries (read-only)
- Logging to Quarkus log system
- Quarkus configuration property population

---

## Compatibility & Deprecation

**Backward Compatibility**:
- Existing `NatsDevServicesBuildTimeConfiguration` properties still read (for non-discovery fallback)
- Existing applications using property-based config continue to work
- Discovery takes precedence over properties (discovery-first)

**Future Deprecation Path** (out of scope for this feature):
- Deprecate property-based dev services configuration in a future version
- Eventually remove the deprecated configuration properties
- Enforce docker-compose as sole dev provisioning mechanism

---

## Testing Expectations

**Unit Tests**:
- Mock `ComposeLocator.locateContainer()` to return test containers
- Verify credential extraction logic
- Test configuration property mapping
- Test error/logging paths

**Integration Tests**:
- Start real docker-compose NATS service
- Run Quarkus application with processor
- Verify application connects to discovered NATS container
- Test with multiple credential configurations
- Test with and without TLS enabled

**Native Image Tests**:
- Same integration tests run as `@QuarkusIntegrationTest` (native)
- Verify no GraalVM reflection issues
- No additional native-specific considerations required

---
