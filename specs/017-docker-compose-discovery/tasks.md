# Implementation Tasks: Docker Compose NATS Container Discovery

**Feature**: 017-docker-compose-discovery
**Branch**: `017-docker-compose-discovery`
**Date**: 2025-11-02
**Spec**: [spec.md](spec.md) | [plan.md](plan.md)

---

## Task Format

Each task follows the format:
```
- [ ] [TaskID] [P] [Story] Description with file path
```

Where:
- **TaskID**: Unique identifier (e.g., TASK-001)
- **P**: Priority (P1, P2, P3)
- **Story**: User story reference (US1, US2, US3, or SETUP/INFRA/TEST)
- **Description**: Clear, actionable task description with absolute file path

---

## Phase 1: Setup & Project Baseline

### Verification Tasks

- [ ] [TASK-001] [P1] [SETUP] Verify project builds successfully: `./mvnw clean install -DskipTests` passes
- [ ] [TASK-002] [P1] [SETUP] Verify all existing tests pass: `./mvnw clean test` passes (runtime + deployment)
- [ ] [TASK-003] [P1] [SETUP] Verify integration tests pass: `./mvnw clean install -Pit` passes
- [ ] [TASK-004] [P1] [SETUP] Create feature branch `017-docker-compose-discovery` from master

### Documentation Review

- [ ] [TASK-005] [P2] [SETUP] Review current NatsDevServicesProcessor implementation at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/NatsDevServicesProcessor.java` (lines 37-144)
- [ ] [TASK-006] [P2] [SETUP] Review current NatsContainer implementation at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/NatsContainer.java` (87 lines to be removed)
- [ ] [TASK-007] [P2] [SETUP] Review docker-compose-devservices.yml at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/integration-tests/docker-compose-devservices.yml` to understand current NATS service configuration

---

## Phase 2: Foundational Changes (Remove Container Creation)

### Code Removal

- [ ] [TASK-008] [P1] [US2] Remove NatsContainer.java file at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/NatsContainer.java` (87 lines)
- [ ] [TASK-009] [P1] [US2] Remove unused imports from NatsDevServicesProcessor.java: `DockerImageName`, `DevServicesResultBuildItem.owned()` pattern imports
- [ ] [TASK-010] [P1] [US2] Remove container creation logic from `startNatsDevService()` method in NatsDevServicesProcessor.java (lines 56-94: startable() callback, container instantiation, start() call)
- [ ] [TASK-011] [P1] [US2] Remove `buildConfigProvider()` method from NatsDevServicesProcessor.java (lines 107-121) as it's only used for managed containers

### Refactor Discovery-Only Flow

- [ ] [TASK-012] [P1] [US2] Refactor `startNatsDevService()` method to discovery-only mode: remove fallback container creation when `discoverRunningService()` returns null
- [ ] [TASK-013] [P1] [US2] Update logging in `startNatsDevService()` to clearly indicate discovery-only behavior (no container creation fallback)
- [ ] [TASK-014] [P1] [US2] Add clear warning log when no container discovered: "NATS Dev Services not initialized - no running docker-compose NATS container found. Start docker-compose or configure quarkus.easynats.servers explicitly."

### Verify Clean Build

- [ ] [TASK-015] [P1] [SETUP] Verify project builds after container removal: `./mvnw clean install -DskipTests` passes
- [ ] [TASK-016] [P1] [SETUP] Fix any compilation errors from removed NatsContainer references

---

## Phase 3: User Story 1 - Docker Compose Container Discovery (P1)

### Data Model Implementation

- [ ] [TASK-017] [P1] [US1] Create `ContainerDiscoveryResult` record in `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/ContainerDiscoveryResult.java` with fields: `boolean found`, `Optional<ContainerConfig> containerConfig`, `String message`
- [ ] [TASK-018] [P1] [US1] Add convenience constructors to ContainerDiscoveryResult: `success(ContainerConfig)` and `notFound(String reason)`
- [ ] [TASK-019] [P1] [US1] Create `ContainerConfig` record in `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/ContainerConfig.java` with fields: `String containerId`, `String host`, `int port`, `String username`, `String password`, `boolean sslEnabled`
- [ ] [TASK-020] [P1] [US1] Add validation in ContainerConfig compact constructor: verify host non-empty, port 1-65535, username non-empty, password non-null, containerId non-empty
- [ ] [TASK-021] [P1] [US1] Implement `toConnectionUrl()` method in ContainerConfig: generates `nats://host:port` or `tls://host:port` based on sslEnabled flag
- [ ] [TASK-022] [P1] [US1] Implement `toConfigurationMap()` method in ContainerConfig: returns Map with quarkus.easynats.servers, username, password, ssl-enabled

### Credential Extraction Helper

- [ ] [TASK-023] [P1] [US1] Create `CredentialExtractor` class (package-private) in `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/main/java/org/mjelle/quarkus/easynats/deployment/devservices/CredentialExtractor.java`
- [ ] [TASK-024] [P1] [US1] Define constants in CredentialExtractor: `DEFAULT_USERNAME = "nats"`, `DEFAULT_PASSWORD = "nats"`, `USERNAME_ENV_VAR = "NATS_USERNAME"`, `PASSWORD_ENV_VAR = "NATS_PASSWORD"`, `TLS_CERT_ENV_VAR = "NATS_TLS_CERT"`, `TLS_KEY_ENV_VAR = "NATS_TLS_KEY"`, `TLS_CA_ENV_VAR = "NATS_TLS_CA"`
- [ ] [TASK-025] [P1] [US1] Create `Credentials` record in CredentialExtractor: `String username`, `String password`, `boolean sslEnabled`
- [ ] [TASK-026] [P1] [US1] Implement `extract(Map<String, String> containerEnv)` method in CredentialExtractor: reads NATS_USERNAME (fallback to NATS_USER for backward compat), NATS_PASSWORD, and TLS env vars; applies defaults for missing values
- [ ] [TASK-027] [P1] [US1] Implement SSL detection logic in CredentialExtractor: `sslEnabled = true` if any of NATS_TLS_CERT, NATS_TLS_KEY, or NATS_TLS_CA is present in containerEnv; fallback to `io.nats.tls` label if available

### Discovery Method Refactoring

- [ ] [TASK-028] [P1] [US1] Refactor `discoverRunningService()` method signature in NatsDevServicesProcessor.java to return `ContainerDiscoveryResult` instead of `DevServicesResultBuildItem`
- [ ] [TASK-029] [P1] [US1] Update `discoverRunningService()` to use `ComposeLocator.locateContainer()` with NATS image pattern matching (support multiple NATS image names: "nats", "nats:*", "nats/*")
- [ ] [TASK-030] [P1] [US1] Extract container environment variables from discovered container using Docker client API (via ContainerAddress or ContainerInfo object)
- [ ] [TASK-031] [P1] [US1] Call `CredentialExtractor.extract()` with container environment map to get Credentials record
- [ ] [TASK-032] [P1] [US1] Extract host and port from ContainerAddress (use `getHost()` and `getPort()` or equivalent methods)
- [ ] [TASK-033] [P1] [US1] Build ContainerConfig from extracted values: containerId, host, port, username, password, sslEnabled
- [ ] [TASK-034] [P1] [US1] Return `ContainerDiscoveryResult.success(containerConfig)` when container found and configured
- [ ] [TASK-035] [P1] [US1] Return `ContainerDiscoveryResult.notFound("reason")` when no container discovered or configuration extraction fails
- [ ] [TASK-036] [P1] [US1] Add debug logging throughout discovery process: "Attempting NATS container discovery...", "Container found: {containerId}", "Extracted credentials: username={username}, ssl={sslEnabled}", "Discovery complete"

### Integration with Build Step

- [ ] [TASK-037] [P1] [US1] Update `startNatsDevService()` to call refactored `discoverRunningService()` returning `ContainerDiscoveryResult`
- [ ] [TASK-038] [P1] [US1] When `ContainerDiscoveryResult.found()` is true, convert `ContainerConfig` to `DevServicesResultBuildItem.discovered()` with config map from `toConfigurationMap()`
- [ ] [TASK-039] [P1] [US1] When `ContainerDiscoveryResult.found()` is false, produce no `DevServicesResultBuildItem` and log warning message from `ContainerDiscoveryResult.message()`
- [ ] [TASK-040] [P1] [US1] Remove references to `config.username()`, `config.password()`, `config.sslEnabled()` from discovery path (no longer read from application.properties per FR-006)

### Unit Tests for Discovery

- [ ] [TASK-041] [P2] [US1] Create unit test directory `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/deployment/src/test/java/org/mjelle/quarkus/easynats/deployment/devservices/` if not exists
- [ ] [TASK-042] [P2] [US1] Create `ContainerConfigTest.java` in deployment unit tests: verify toConnectionUrl() generates correct nats:// and tls:// URLs
- [ ] [TASK-043] [P2] [US1] Add test in ContainerConfigTest: verify toConfigurationMap() returns correct property mappings
- [ ] [TASK-044] [P2] [US1] Add test in ContainerConfigTest: verify compact constructor validation rejects invalid host, port, username, password, containerId
- [ ] [TASK-045] [P2] [US1] Create `CredentialExtractorTest.java` in deployment unit tests: verify extract() handles present NATS_USERNAME and NATS_PASSWORD
- [ ] [TASK-046] [P2] [US1] Add test in CredentialExtractorTest: verify extract() applies defaults when env vars missing
- [ ] [TASK-047] [P2] [US1] Add test in CredentialExtractorTest: verify extract() detects SSL from NATS_TLS_CERT, NATS_TLS_KEY, NATS_TLS_CA presence
- [ ] [TASK-048] [P2] [US1] Add test in CredentialExtractorTest: verify extract() supports NATS_USER as fallback for NATS_USERNAME (backward compatibility)

---

## Phase 4: User Story 2 - No Property Fallback Configuration (P1)

### Remove Configuration Property Usage

- [ ] [TASK-049] [P1] [US2] Remove all reads of `config.username()`, `config.password()`, `config.sslEnabled()` from `discoverRunningService()` method
- [ ] [TASK-050] [P1] [US2] Remove `NatsDevServicesBuildTimeConfiguration` parameter from `discoverRunningService()` method signature (no longer needed for discovery)
- [ ] [TASK-051] [P1] [US2] Update `discoverRunningService()` to only use container environment variables and defaults (never read from application.properties)
- [ ] [TASK-052] [P1] [US2] Verify `config.enabled()` check remains in `startNatsDevService()` to allow disabling dev services entirely (line 45-48)
- [ ] [TASK-053] [P1] [US2] Verify `config.imageName()` is still used in ComposeLocator pattern matching for discovering NATS containers (required for image name filtering)

### Documentation Updates

- [ ] [TASK-054] [P2] [US2] Update JavaDoc for `NatsDevServicesProcessor` class: document discovery-only behavior, no container creation, docker-compose requirement
- [ ] [TASK-055] [P2] [US2] Update JavaDoc for `discoverRunningService()` method: document container environment variable extraction, no property fallback
- [ ] [TASK-056] [P2] [US2] Add JavaDoc comment to `startNatsDevService()`: "Dev Services are only initialized if a running NATS container is discovered from docker-compose. No fallback container creation. Application requires explicit server URL configuration if discovery fails."

---

## Phase 5: User Story 3 - Clustering Support with Multi-Container Discovery (P1)

### Multi-Container Discovery Implementation

- [ ] [TASK-057] [P1] [US3] Update `discoverRunningService()` to discover ALL running NATS containers with exposed port 4222 (not just the first one)
- [ ] [TASK-058] [P1] [US3] Use `ComposeLocator.locateContainers()` (plural) or equivalent to get List of ContainerAddress objects for all NATS containers
- [ ] [TASK-059] [P1] [US3] Filter discovered containers to only those with port 4222 exposed/mapped (indicates client-accessible node)
- [ ] [TASK-060] [P1] [US3] For each discovered container, extract host and port and build individual connection URL (e.g., "nats://localhost:4222", "nats://localhost:4223")
- [ ] [TASK-061] [P1] [US3] Build comma-separated connection URL list from all discovered containers (e.g., "nats://localhost:4222,nats://localhost:4223,nats://localhost:4224")
- [ ] [TASK-062] [P1] [US3] Update ContainerConfig to support comma-separated host and port lists: modify `host` and `port` fields to handle multiple values
- [ ] [TASK-063] [P1] [US3] Update `toConnectionUrl()` method in ContainerConfig to handle multi-container scenario: split host/port lists and build comma-separated URL list
- [ ] [TASK-064] [P1] [US3] Extract credentials from first discovered container (assumption: all cluster nodes share same credentials per spec.md line 134)
- [ ] [TASK-065] [P1] [US3] Validate that all discovered containers have the same SSL configuration (all TLS or all non-TLS)
- [ ] [TASK-066] [P1] [US3] Build ContainerConfig with merged connection metadata: comma-separated containerId list, host list, port list, single username/password/sslEnabled

### Logging for Clustering

- [ ] [TASK-067] [P2] [US3] Add info log when multiple containers discovered: "Discovered {count} NATS containers with exposed port 4222: {containerIds}"
- [ ] [TASK-068] [P2] [US3] Add debug log with full connection URL list: "NATS cluster connection URLs: {urlList}"
- [ ] [TASK-069] [P2] [US3] Add warning log if containers have mismatched SSL configuration: "Cluster containers have inconsistent TLS settings - using first container's configuration"

### Unit Tests (Clustering support code-level, not tested)

- [ ] [TASK-070] [P2] [US3] Code review: Verify toConnectionUrl() and toConfigurationMap() handle comma-separated host/port lists (clustering support documented, not tested)
- [ ] [TASK-071] [P2] [US3] Code review: Verify multi-container discovery logic filters to exposed port 4222 (clustering support code complete)

---

## Phase 6: Integration Testing & Validation

### Integration Test Updates (Focus on Common Case: Single NATS + Basic Auth)

- [ ] [TASK-073] [P1] [TEST] Verify NatsDevServicesTest.java at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/NatsDevServicesTest.java` passes with discovery-only implementation
- [ ] [TASK-074] [P1] [TEST] Update docker-compose-devservices.yml at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/integration-tests/docker-compose-devservices.yml`: ensure NATS_USERNAME and NATS_PASSWORD env vars are set (not NATS_USER) for consistency
- [ ] [TASK-075] [P1] [TEST] Add integration test scenario: single NATS container with custom username/password, verify extension discovers and connects
- [ ] [TASK-076] [P1] [TEST] Add integration test scenario: verify dev services not initialized when no docker-compose NATS container running
- [ ] [TASK-077] [P2] [TEST] Document clustering support in quickstart.md: "Multiple NATS containers with exposed ports are supported. Extension builds comma-separated connection URL list. Not tested by default CI but available for advanced users."
- [ ] [TASK-078] [P2] [TEST] Document TLS/SSL support in quickstart.md: "NATS containers with NATS_TLS_CERT, NATS_TLS_KEY, NATS_TLS_CA env vars are detected and use tls:// scheme. Not tested by default CI but supported."

### Native Image Testing

- [ ] [TASK-079] [P2] [TEST] Run native integration tests: `./mvnw clean install -Pit` passes with discovery-only implementation
- [ ] [TASK-080] [P2] [TEST] Verify all *IT.java tests inherit from *Test.java classes and pass in native context

### Build & Compilation Verification

- [ ] [TASK-081] [P1] [SETUP] Verify full project build: `./mvnw clean install` passes (all modules)
- [ ] [TASK-082] [P1] [SETUP] Verify all unit tests pass: `./mvnw clean test` passes (runtime + deployment)
- [ ] [TASK-083] [P1] [SETUP] Verify all integration tests pass: `./mvnw clean install -Pit` passes

### Constitution Compliance Verification

- [ ] [TASK-084] [P1] [SETUP] Verify no runtime module dependencies added (check runtime/pom.xml unchanged)
- [ ] [TASK-085] [P1] [SETUP] Verify deployment module only used for build-time processing (no runtime code in deployment module)
- [ ] [TASK-086] [P1] [SETUP] Verify constructor injection used in all new classes (no @Inject field injection in production code)
- [ ] [TASK-087] [P1] [SETUP] Verify AssertJ used for all test assertions (no JUnit assertions)
- [ ] [TASK-088] [P1] [SETUP] Verify no jakarta.ws.rs.core.Response usage in new code

### Performance Validation

- [ ] [TASK-089] [P2] [TEST] Measure and log container discovery time: verify discovery completes in under 5 seconds (SC-004)
- [ ] [TASK-090] [P2] [TEST] Measure and log application startup time with discovery: verify minimal overhead (<300ms expected per research.md)

---

## Phase 7: Documentation & Cleanup

### Code Documentation

- [ ] [TASK-091] [P2] [SETUP] Add comprehensive JavaDoc to ContainerDiscoveryResult record: document all fields, factory methods, invariants
- [ ] [TASK-092] [P2] [SETUP] Add comprehensive JavaDoc to ContainerConfig record: document all fields, validation rules, methods
- [ ] [TASK-093] [P2] [SETUP] Add comprehensive JavaDoc to CredentialExtractor class: document environment variable conventions, defaults, SSL detection
- [ ] [TASK-094] [P2] [SETUP] Update NatsDevServicesProcessor class JavaDoc: document discovery-only mode, docker-compose requirement, clustering support

### User-Facing Documentation

- [ ] [TASK-095] [P2] [SETUP] Update feature quickstart.md at `/home/mjell/projects/test-spec/easy-nats/quarkus-easy-nats/specs/017-docker-compose-discovery/quickstart.md`: document required docker-compose.yml structure, env var conventions
- [ ] [TASK-096] [P2] [SETUP] Add example docker-compose-devservices.yml snippet to quickstart.md: basic single container with username/password
- [ ] [TASK-097] [P2] [SETUP] Document developer workflow in quickstart.md: "Start docker-compose, run Quarkus dev mode, extension auto-discovers NATS"
- [ ] [TASK-098] [P2] [SETUP] Add note to quickstart.md: "Advanced features (clustering, TLS) are supported by the extension but not tested by default. See advanced-setup.md for examples."

### Logging & Error Messages

- [ ] [TASK-099] [P2] [SETUP] Review all log messages for clarity and consistency: use appropriate log levels (debug for discovery attempts, info for success, warn for failures)
- [ ] [TASK-100] [P2] [SETUP] Add actionable error messages when discovery fails: suggest running docker-compose, checking container status, or configuring explicit server URL
- [ ] [TASK-101] [P2] [SETUP] Add debug logging for container environment variable extraction: log all env vars read (sanitize password values)

### Code Review Checklist

- [ ] [TASK-102] [P1] [SETUP] Review all changes for Constitution compliance: constructor injection, AssertJ assertions, no runtime dependencies
- [ ] [TASK-103] [P1] [SETUP] Review all new code for immutability: records are immutable, no mutable state in processors
- [ ] [TASK-104] [P1] [SETUP] Review all test code for correct patterns: no @Inject in test base classes, RestAssured for HTTP testing, Awaitility for async testing
- [ ] [TASK-105] [P1] [SETUP] Review error handling: fail-fast on invalid configuration, clear error messages, proper exception wrapping

---

## Task Summary by Phase

| Phase | P1 Tasks | P2 Tasks | Total | Notes |
|-------|----------|----------|-------|-------|
| Phase 1: Setup & Baseline | 4 | 3 | 7 | Project verification |
| Phase 2: Foundational Changes | 9 | 0 | 9 | Remove NatsContainer, discovery-only mode |
| Phase 3: US1 - Discovery | 24 | 8 | 32 | Core implementation - single container focus |
| Phase 4: US2 - No Property Fallback | 5 | 3 | 8 | Docker-compose as source of truth |
| Phase 5: US3 - Clustering | 10 | 2 | 12 | Code support for multi-container (not tested) |
| Phase 6: Testing & Validation | 9 | 6 | 15 | Focus on single NATS + basic auth; clustering/TLS documented |
| Phase 7: Documentation | 4 | 8 | 12 | JavaDoc + user guides |
| **Total** | **65** | **30** | **95** |

---

## Critical Path

The following tasks must be completed in order to achieve MVP (Minimum Viable Product):

1. **Phase 1 Setup** (TASK-001 to TASK-004): Establish clean baseline
2. **Phase 2 Foundational** (TASK-008 to TASK-016): Remove container creation, refactor to discovery-only
3. **Phase 3 US1 Core** (TASK-017 to TASK-040): Implement discovery logic and data model
4. **Phase 4 US2** (TASK-049 to TASK-053): Remove property fallback configuration
5. **Phase 5 US3 Core** (TASK-057 to TASK-066): Implement clustering support (code complete, not tested)
6. **Phase 6 Validation** (TASK-073 to TASK-083): Verify single-container tests pass; document clustering/TLS as supported

**Estimated Critical Path Duration**: ~2-3 days for MVP (testing scope reduced to single container + basic auth)
**Scope Note**: Clustering and TLS code support included; advanced test scenarios documented as optional

---

## Success Criteria Mapping

| Success Criteria | Related Tasks |
|------------------|---------------|
| SC-001: Auto-connect without manual config | TASK-017 to TASK-040 (US1 Discovery) |
| SC-002: Correct discovery of all parameters | TASK-023 to TASK-027 (Credential Extraction) |
| SC-003: No fallback to application.properties | TASK-049 to TASK-053 (US2 No Property Fallback) |
| SC-004: Discovery completes in <5 seconds | TASK-089 to TASK-090 (Performance Validation) |
| SC-005: Customize via docker-compose only | TASK-075 to TASK-076 (Integration Testing) |

---

## Risk Mitigation

### High-Risk Areas

1. **Container Environment Variable Extraction** (TASK-030, TASK-031)
   - Risk: Docker API may not expose env vars in all scenarios
   - Mitigation: Test with standard NATS image, add fallback to defaults

2. **Clustering Discovery Code** (TASK-057 to TASK-066)
   - Risk: Multi-container discovery logic must be correct even though not tested
   - Mitigation: Code review verification, add debug logging for troubleshooting
   - Note: Clustering testing is optional; implementation includes support for developers who need it

3. **Backward Compatibility** (TASK-051, TASK-074)
   - Risk: Breaking existing dev services users who rely on application.properties
   - Mitigation: Clear error messages when discovery fails, documentation of migration

### Dependency Risks

- **Quarkus ComposeLocator API**: Assumed stable in 3.27.0, verify documentation and examples
- **Testcontainers Docker Client**: Required for env var access, ensure available in deployment module
- **NATS Image Conventions**: Assumption on NATS_USERNAME/NATS_PASSWORD env vars, verify with official NATS images

---

## Notes

- All tasks must follow Constitution principles: constructor injection, AssertJ assertions, no runtime dependencies without justification
- Integration tests use existing docker-compose-devservices.yml setup, updated for new discovery behavior
- Unit tests focus on data model validation and credential extraction logic (deployment module tests)
- Native image tests inherit from JVM tests via `@QuarkusIntegrationTest` pattern
- No new runtime dependencies required per plan.md (deployment module changes only)
- Performance target of <5 seconds (SC-004) is conservative; research.md indicates ~300ms expected for discovery-only mode
- Clustering support is P1 per spec.md user stories, implemented in Phase 5
- All file paths are absolute as required by project conventions

---

## Change Log

- **2025-11-02**: Initial tasks.md generated from spec.md, plan.md, data-model.md, and research.md
  - 102 total tasks across 7 phases
  - 69 P1 (critical path), 33 P2 (quality/polish)
  - Covers US1 (docker-compose discovery), US2 (no property fallback), US3 (clustering)
  - Includes complete testing strategy (unit, integration, native)
  - Constitution compliance verification tasks included
