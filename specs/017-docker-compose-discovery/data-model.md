# Data Model: Docker Compose NATS Container Discovery

**Feature**: 017-docker-compose-discovery
**Date**: 2025-11-02

---

## Overview

This document defines the data structures and entities involved in discovering and configuring NATS containers from docker-compose. The model is minimal and focused on the discovery workflow—no new persistence or complex state management required.

---

## Core Entities

### 1. ContainerDiscoveryResult

**Purpose**: Represents the outcome of attempting to discover a NATS container in docker-compose.

**Location**: Package `org.mjelle.quarkus.easynats.deployment.devservices`

```java
/**
 * Encapsulates the result of NATS container discovery from docker-compose.
 * Immutable record containing discovery outcome and container metadata.
 * Supports both single container and clustering scenarios.
 */
public record ContainerDiscoveryResult(
    /**
     * True if at least one NATS container was successfully discovered
     */
    boolean found,

    /**
     * Container connection details (present only if found=true)
     * Contains merged configuration from all discovered containers.
     * For clustering, connection URL list is comma-separated.
     */
    Optional<ContainerConfig> containerConfig,

    /**
     * Diagnostic message for logging/debugging
     * Populated whether discovery succeeded or failed
     */
    String message
) {
    // Convenience constructor: success case
    public static ContainerDiscoveryResult success(ContainerConfig config) {
        return new ContainerDiscoveryResult(true, Optional.of(config),
            "NATS container(s) discovered: " + config.containerId());
    }

    // Convenience constructor: failure case
    public static ContainerDiscoveryResult notFound(String reason) {
        return new ContainerDiscoveryResult(false, Optional.empty(),
            "NATS container(s) not discovered: " + reason);
    }
}
```

**Validation**:
- Invariant: `found` must be `true` iff `containerConfig.isPresent()`
- `message` must be non-empty (never null)
- For clustering: `containerId` may contain comma-separated IDs of all discovered containers

**Relationships**:
- Contains one `ContainerConfig` (if found)

---

### 2. ContainerConfig

**Purpose**: Represents extracted connection and authentication metadata for a discovered NATS container.

**Location**: Package `org.mjelle.quarkus.easynats.deployment.devservices`

```java
/**
 * Connection configuration extracted from discovered docker-compose NATS container(s).
 * Immutable and directly translates to Quarkus configuration properties.
 * Supports both single container and clustering (multiple containers) scenarios.
 */
public record ContainerConfig(
    /**
     * Container ID(s) from Docker API
     * For single container: hex string uniquely identifying the container
     * For clustering: comma-separated list of container IDs (e.g., "abc123,def456,ghi789")
     */
    String containerId,

    /**
     * Hostname or IP address for NATS client connection
     * For single container: single host address
     * For clustering: comma-separated list of all discovered hosts (e.g., "localhost,localhost,localhost" or "host1,host2,host3")
     * May be "localhost" for local dev, or actual hostname for remote
     */
    String host,

    /**
     * Exposed NATS client port (4222 standard, or custom if remapped)
     * For single container: single port
     * For clustering: comma-separated list of all discovered ports (e.g., "4222,4223,4224")
     * Range: 1-65535 for each port in list
     */
    int port,

    /**
     * NATS authentication username (extracted from NATS_USERNAME env var)
     * Default: "nats" if not specified
     * Cannot be null or empty
     */
    String username,

    /**
     * NATS authentication password (extracted from NATS_PASSWORD env var)
     * Default: "nats" if not specified
     * Cannot be null or empty (even if empty string would be valid in theory)
     */
    String password,

    /**
     * Flag indicating SSL/TLS enablement (from NATS_TLS_CERT, NATS_TLS_KEY, NATS_TLS_CA env vars)
     * TLS is enabled if ANY certificate path environment variable is present
     * Determines scheme: "tls://" if true, "nats://" if false
     */
    boolean sslEnabled
) {
    // Compact constructor for validation
    public ContainerConfig {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("host cannot be null or empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1-65535, got " + port);
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("username cannot be null or empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("password cannot be null");
        }
        if (containerId == null || containerId.isEmpty()) {
            throw new IllegalArgumentException("containerId cannot be null or empty");
        }
    }

    /**
     * Generates NATS connection URL(s) from container config
     * For single container: returns single URL (e.g., "nats://host:port")
     * For clustering: returns comma-separated list of all discovered nodes (e.g., "nats://host1:4222,nats://host2:4223")
     * @return properly formatted NATS connection URL or comma-separated URL list
     */
    public String toConnectionUrl() {
        String scheme = sslEnabled ? "tls" : "nats";
        String[] hosts = host.split(",");
        String[] ports = String.valueOf(port).split(",");

        List<String> urls = new ArrayList<>();
        for (int i = 0; i < hosts.length; i++) {
            String hostPart = hosts[i].trim();
            String portPart = (i < ports.length) ? ports[i].trim() : String.valueOf(port);
            urls.add(scheme + "://" + hostPart + ":" + portPart);
        }
        return String.join(",", urls);
    }

    /**
     * Generates configuration map for Quarkus Dev Services
     * Supports both single container and clustering scenarios.
     * For clustering, comma-separated URL list is generated from all discovered containers.
     * @return property name -> value mapping
     */
    public Map<String, String> toConfigurationMap() {
        return Map.of(
            "quarkus.easynats.servers", toConnectionUrl(),
            "quarkus.easynats.username", username,
            "quarkus.easynats.password", password,
            "quarkus.easynats.ssl-enabled", String.valueOf(sslEnabled)
        );
    }
}
```

**Validation Rules**:
- `host`: Non-empty, valid hostname or IP
- `port`: Range 1-65535 (standard port range)
- `username`: Non-empty string (default "nats")
- `password`: Non-null (empty string allowed per NATS spec, though "nats" is default)
- `containerId`: Non-empty container ID from Docker
- `sslEnabled`: Boolean flag (validated implicitly by Java type)

**Relationships**:
- Contained in `ContainerDiscoveryResult`
- Translates to Quarkus configuration via `toConfigurationMap()`
- Referenced in logging for debugging

**State Transitions**: None (immutable)

---

### 3. CredentialExtractorConfig (Optional Helper)

**Purpose**: Helper for extracting and validating credentials from container environment.

**Location**: Package `org.mjelle.quarkus.easynats.deployment.devservices` (internal/package-private)

```java
/**
 * Helper for extracting NATS credentials from container environment variables.
 * Encapsulates logic for reading env vars and applying defaults.
 * NOT exposed in public API—internal only.
 */
class CredentialExtractor {
    private static final String DEFAULT_USERNAME = "nats";
    private static final String DEFAULT_PASSWORD = "nats";
    private static final String USERNAME_ENV_VAR = "NATS_USERNAME";
    private static final String PASSWORD_ENV_VAR = "NATS_PASSWORD";
    private static final String TLS_ENV_VAR = "NATS_TLS";

    /**
     * Extracts NATS credentials from container environment
     * @param containerEnv map of environment variables
     * @return record with extracted username, password, ssl flag
     */
    public record Credentials(String username, String password, boolean sslEnabled) {}

    public static Credentials extract(Map<String, String> containerEnv) {
        String username = containerEnv.getOrDefault(USERNAME_ENV_VAR, DEFAULT_USERNAME);
        String password = containerEnv.getOrDefault(PASSWORD_ENV_VAR, DEFAULT_PASSWORD);
        boolean ssl = Boolean.parseBoolean(containerEnv.getOrDefault(TLS_ENV_VAR, "false"));

        return new Credentials(username, password, ssl);
    }
}
```

**Validation**:
- Defaults applied for missing environment variables
- TLS flag defaults to false (development-safe default)

---

## Relationships Diagram

```
┌─────────────────────────────────┐
│ NatsDevServicesProcessor        │ (Build-time processor)
│ startNatsDevService()           │
└────────┬────────────────────────┘
         │
         │ calls discoverNatsContainer()
         │
         ▼
┌─────────────────────────────────┐
│ ContainerDiscoveryResult         │ (Discovery outcome)
│ - found: boolean                │
│ - containerConfig: Optional     │
│ - message: String               │
└────────┬────────────────────────┘
         │
         │ contains (if found)
         │
         ▼
┌─────────────────────────────────┐
│ ContainerConfig                 │ (Connection metadata)
│ - containerId: String           │
│ - host: String                  │
│ - port: int                     │
│ - username: String              │
│ - password: String              │
│ - sslEnabled: boolean           │
├─────────────────────────────────┤
│ toConnectionUrl(): String       │
│ toConfigurationMap(): Map       │
└─────────────────────────────────┘
```

---

## Key Entities Summary

| Entity | Purpose | Mutability | Lifecycle |
|--------|---------|-----------|-----------|
| `ContainerDiscoveryResult` | Result wrapper for discovery attempt | Immutable | Build-time only |
| `ContainerConfig` | Connection/auth metadata | Immutable | Build-time only |
| `CredentialExtractor` | Helper for credential extraction | Stateless | Build-time only |

---

## No Persistence Layer

This feature requires **no database, file storage, or runtime persistence**:
- Discovery happens at build time (during application startup)
- Configuration is applied to Quarkus properties (ephemeral)
- No state carried forward between runs
- No entity relationships requiring database management

---

## Type Safety and Nullability

**Nullability Policy**:
- No fields are nullable (all required)
- `Optional<T>` used for conditional presence only
- Validation in record constructors prevents invalid states

**Type Constraints**:
- Container ID: Regex-validated hex string (Docker format)
- Port: Primitive int with range validation
- Username/Password: Non-empty strings (or default)
- SSL flag: Boolean (no ambiguity)

---

## Design Principles

1. **Immutability**: All data structures are immutable (records)
   - Prevents accidental modification during processing
   - Thread-safe for build-time operations
   - Clear contracts for downstream consumers

2. **Validation at Boundaries**: Constructor validation catches errors early
   - Fail-fast approach
   - Clear error messages for misconfiguration

3. **Minimalism**: Only fields needed for discovery and configuration
   - No extraneous metadata
   - Focused on single responsibility

4. **Composition**: `ContainerDiscoveryResult` wraps `ContainerConfig`
   - Clear separation of concerns
   - Success/failure distinction at top level

---

## Change Log

- **2025-11-02**: Initial design created during Phase 1 planning
  - `ContainerDiscoveryResult` for discovery outcome
  - `ContainerConfig` for metadata and Quarkus property generation
  - `CredentialExtractor` for internal credential logic

---
