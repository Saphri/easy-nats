# Feature Specification: Docker Compose NATS Container Discovery

**Feature Branch**: `017-docker-compose-discovery`
**Created**: 2025-11-02
**Status**: Draft
**Input**: User description: "Modify NatsDevServicesProcessor to only discover the running nats container and extract the needed username, password, port and host. So the user cannot use application.properties to configure devservices but will provide a docker compose file that starts nats like he wants"

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Developers use docker-compose to provision NATS (Priority: P1)

A developer wants to run NATS in their local development environment using docker-compose. They define a docker-compose file with NATS service configuration including custom credentials, port, and optional SSL settings. When they start the Quarkus application, dev services automatically discovers the running NATS container and connects to it without requiring any configuration in application.properties.

**Why this priority**: This is the core value of the feature - enabling developers to use docker-compose for NATS provisioning without manual configuration.

**Independent Test**: Can be tested by starting a docker-compose with NATS, running a Quarkus application with the extension, and verifying the application automatically connects to the discovered NATS instance.

**Acceptance Scenarios**:

1. **Given** a docker-compose file with NATS service is running with default credentials, **When** a Quarkus application with the extension starts, **Then** the Dev Services processor discovers the NATS container and automatically configures the connection URL
2. **Given** a docker-compose file with NATS service using custom username and password, **When** a Quarkus application starts, **Then** the Dev Services processor extracts these credentials and configures them automatically
3. **Given** a docker-compose file with NATS service on a non-standard port, **When** a Quarkus application starts, **Then** the Dev Services processor discovers and uses the correct port
4. **Given** a docker-compose file with NATS service using TLS, **When** a Quarkus application starts, **Then** the Dev Services processor detects SSL and uses the appropriate scheme (tls://)

---

### User Story 2 - No configuration fallback to user properties (Priority: P1)

The Dev Services processor no longer reads or applies configuration from application.properties for NATS settings. If no docker-compose NATS container is discovered, Dev Services are not initialized and the application requires explicit server URL configuration.

**Why this priority**: This enforces the contract that docker-compose is the sole mechanism for NATS provisioning in dev mode, preventing confusion from multiple configuration sources.

**Independent Test**: Can be tested by running a Quarkus application without a running docker-compose NATS container and verifying that Dev Services do not start.

**Acceptance Scenarios**:

1. **Given** no docker-compose NATS container is running, **When** a Quarkus application starts, **Then** Dev Services are not initialized
2. **Given** quarkus.easynats.servers is configured in application.properties but no docker-compose NATS is running, **When** a Quarkus application starts, **Then** Dev Services are not used and the configured server URL is required at runtime
3. **Given** application.properties contains devservices configuration properties, **When** a Quarkus application starts with a running docker-compose NATS, **Then** the discovered container values take precedence and application.properties values are ignored

---

### User Story 3 - Dev Services discovers containers from docker-compose (Priority: P1)

The Dev Services processor detects and connects to NATS containers that are part of the application's docker-compose project, similar to how other Quarkus dev services discover external infrastructure.

**Why this priority**: Ensures that the discovery mechanism integrates with Quarkus's existing dev services infrastructure and docker-compose workflow.

**Independent Test**: Can be tested by verifying that the processor correctly identifies running NATS containers from docker-compose and extracts connection metadata.

**Acceptance Scenarios**:

1. **Given** a docker-compose file defines a NATS service with environment variables for authentication, **When** the service is running, **Then** the Dev Services processor can extract username and password from the container or environment
2. **Given** multiple containers are running in docker-compose, **When** a Quarkus application starts, **Then** the Dev Services processor correctly identifies the NATS container (by image name or label)
3. **Given** a docker-compose NATS container with exposed ports, **When** the Dev Services processor discovers it, **Then** it correctly resolves the host and port for connection

---

### Edge Cases

- What happens when a docker-compose file defines NATS but the container is not running?
- How does the system behave if multiple NATS containers are discovered from the same compose project (clustering)?
  - Should all containers be included in the connection URL list?
  - How are containers identified as part of the same cluster?
- What if some NATS containers in a cluster have different credentials than others?
- What if the NATS container credentials cannot be extracted from environment variables or container metadata?
- How does the processor handle NATS containers without explicit credentials set?
- What if the docker-compose project is stopped and restarted - does discovery adapt to the new container instances?
- For NATS clustering, what order should discovered containers appear in the connection URL list?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: Dev Services processor MUST query running docker containers to discover NATS service(s)
- **FR-002**: Processor MUST extract connection host from discovered NATS container(s)
- **FR-003**: Processor MUST extract connection port from discovered NATS container(s) (default to standard NATS port if not customized)
- **FR-004**: Processor MUST extract username credential from container environment variables or metadata
- **FR-005**: Processor MUST extract password credential from container environment variables or metadata
- **FR-006**: Processor MUST NOT read NATS configuration from application.properties, application.yaml, or environment variables (e.g., quarkus.easynats.servers, quarkus.easynats.username, quarkus.easynats.password, quarkus.easynats.ssl-enabled)
- **FR-007**: Processor MUST handle NATS containers configured with SSL/TLS and set the correct scheme (nats:// or tls://)
- **FR-008**: Processor MUST support containers that expose NATS on custom ports
- **FR-009**: When no docker-compose NATS container is discovered, processor MUST NOT initialize Dev Services (application must provide explicit configuration)
- **FR-010**: Processor MUST log discovery attempts and results at appropriate levels (debug for attempts, info for discovery success, warn for discovery failure)
- **FR-011**: For multiple discovered NATS containers (clustering), processor MUST build comma-separated connection URL list (e.g., `nats://host1:port1,nats://host2:port2,nats://host3:port3`)

### Key Entities *(include if feature involves data)*

- **NATS Container(s)**: One or more running Docker containers hosting NATS JetStream service (for single node or clustering scenarios) with connection metadata (host, port, credentials, protocol)
- **Dev Services Configuration**: Runtime configuration derived from discovered container(s) (connection URL list, username, password, SSL enabled flag)
- **Docker Compose Project**: User-defined docker-compose file that provisions NATS service(s) and other services for local development

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Developers can start a Quarkus application with NATS Dev Services enabled and a running docker-compose NATS container, and the application automatically connects without manual configuration
- **SC-002**: All connection parameters (host, port, username, password, SSL) are correctly discovered and applied from the docker-compose NATS container
- **SC-003**: When no NATS container is discovered, applications require explicit server URL configuration (no fallback to application.properties)
- **SC-004**: Discovery and configuration application completes in under 5 seconds during application startup
- **SC-005**: Developers can customize NATS configuration (credentials, port, SSL) entirely through docker-compose and it is reflected in the Quarkus application without code changes

## Assumptions

- Docker and docker-compose are installed and available on the developer's machine
- The docker-compose file follows standard conventions for service definition (service name or image name allows identification)
- NATS container environment variables follow standard naming conventions for storing credentials (e.g., NATS_USERNAME, NATS_PASSWORD, or similar)
- Dev Services discovery leverages existing Quarkus container locator utilities and docker integration
- The feature is only relevant for development and test launch modes (not production)
- NATS clustering (multiple containers) supported: extension discovers all NATS containers and builds comma-separated connection URL list
- All NATS containers in a cluster share the same authentication credentials and SSL configuration

## Constraints & Out of Scope

- Does NOT support configuration through application.properties/yaml for NATS Dev Services
- Does NOT manage the lifecycle of docker-compose (assumes user starts it manually or via external tooling)
- Does NOT create or start NATS containers (only discovers already-running containers)
- Does NOT provide UI or dashboard for container discovery status
