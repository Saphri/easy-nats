# Feature Specification: Custom NATS Options via CDI

**Feature Branch**: `016-custom-nats-options`
**Created**: 2025-11-02
**Status**: Draft
**Input**: User description: "as a developer I would like to be able to provide my own Options (io.nats.client.Options) using CDI for Nats connect. This is for avanced user"

## Clarifications

### Session 2025-11-02

- Q: How should the extension configure the default `Options` when no custom bean is provided? → A: The extension will create a `@DefaultBean` CDI producer that reads configuration from `quarkus.easynats.servers`, `quarkus.easynats.username`, `quarkus.easynats.password`, and `quarkus.easynats.ssl-enabled` properties. Developers can override by providing their own unqualified `Options` bean.

- Q: Should the extension support multiple `Options` beans using qualifiers? → A: No. Multiple unqualified `Options` beans are impossible in practice due to CDI's bean resolution rules—CDI itself will throw `AmbiguousResolutionException` at startup. Removed User Story 2 as unrealistic.

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

### User Story 1 - Advanced Developer Provides Custom NATS Options (Priority: P1)

An advanced developer wants to configure the NATS connection with custom `io.nats.client.Options` (e.g., connection timeouts, retry policies, SSL settings, connection pool size) without needing to fork or modify the Quarkus Easy NATS extension. They should be able to define their own `Options` bean in their application using CDI, and the extension should automatically detect and use it.

**Why this priority**: This is the core value of the feature—enabling advanced users to have full control over NATS connection configuration while keeping the extension flexible and extensible. Without this, advanced users are blocked from customizing NATS behavior.

**Independent Test**: An application can provide a custom `Options` bean, the extension detects it, and the NATS connection uses those custom options. This can be verified by checking that the connection is established with the custom settings (e.g., custom timeout, SSL enabled, etc.).

**Acceptance Scenarios**:

1. **Given** a Quarkus application with a CDI bean that produces a custom `io.nats.client.Options`, **When** the Quarkus Easy NATS extension initializes, **Then** the extension uses the custom `Options` to connect to NATS instead of creating default options
2. **Given** a custom `Options` bean is provided with specific connection settings (e.g., 10-second timeout), **When** the NATS connection is established, **Then** those settings are applied to the connection
3. **Given** an application without a custom `Options` bean, **When** the extension initializes, **Then** the extension creates a default `Options` object with sensible defaults

---

### User Story 2 - Custom Options Integration with Existing Extension Features (Priority: P2)

The custom `Options` bean should work seamlessly with other features of the Quarkus Easy NATS extension (e.g., CloudEvents support, typed subscribers, durable consumers). The extension should apply both the custom options and its own configuration without conflicts.

**Why this priority**: This ensures the feature integrates cleanly with the extension ecosystem and doesn't break existing functionality. It's important for long-term maintainability but secondary to the core functionality.

**Independent Test**: An application with a custom `Options` bean can successfully use CloudEvents-wrapped messages and typed subscribers, demonstrating that the custom options don't conflict with other extension features.

**Acceptance Scenarios**:

1. **Given** a custom `Options` bean and a subscriber using CloudEvents, **When** a CloudEvent is published, **Then** the subscriber receives and correctly deserializes the CloudEvent
2. **Given** a custom `Options` bean with non-standard connection settings, **When** a durable consumer is used, **Then** the consumer functions correctly with the custom connection configuration

---

### Edge Cases

- What happens if the custom `Options` bean is `null`? (CDI should prevent injection of null, but document the behavior)
- How does the extension handle `Options` that disable JetStream? (Document whether this is a valid configuration or an error)
- What if the default producer throws an exception while reading properties? (e.g., invalid property value)
- What if the custom `Options` bean throws an exception during instantiation? (CDI will fail startup; document expectations)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The extension MUST provide a CDI producer (using `@DefaultBean` and `@Produces`) that creates a default `Options` bean based on Quarkus Easy NATS configuration properties (`quarkus.easynats.servers`, `quarkus.easynats.username`, `quarkus.easynats.password`, `quarkus.easynats.ssl-enabled`)
- **FR-002**: If a custom `Options` bean is defined in the application (overriding the `@DefaultBean`), the extension MUST use it to create the NATS connection instead of the default
- **FR-003**: The custom `Options` bean MUST be applied before the NATS connection is established
- **FR-004**: Custom `Options` beans and the default producer MUST be compatible with Quarkus's native image compilation (GraalVM)
- **FR-005**: Multiple unqualified `Options` beans MUST result in a CDI `AmbiguousResolutionException` at startup (delegated to CDI, not handled by the extension)

### Key Entities

- **Options Bean**: A CDI-managed bean that produces an instance of `io.nats.client.Options` with custom configuration (e.g., connection timeouts, SSL/TLS settings, authentication, protocol version)
- **NATS Connection**: The established connection to a NATS JetStream broker, created using the `Options` bean if provided

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can provide a custom `Options` bean that overrides the default, and the extension successfully initializes using those options without requiring extension modifications
- **SC-002**: An application with a custom `Options` bean can start and connect to NATS within standard startup time (no significant performance degradation)
- **SC-003**: The default `Options` producer correctly reads configuration from `quarkus.easynats.*` properties and creates appropriate NATS connection options
- **SC-004**: Custom `Options` work seamlessly with existing extension features (CloudEvents, typed subscribers, durable consumers) without conflicts
- **SC-005**: The feature works correctly in both JVM and native image environments

## Assumptions

- The `io.nats.client.Options` class is stable and available from the NATS client library
- Developers using this feature have knowledge of NATS configuration options
- CDI bean discovery is standard and follows Quarkus conventions
- The default `Options` producer uses `@DefaultBean`, allowing it to be overridden by any custom unqualified bean
- Multiple unqualified `Options` beans will be caught by CDI's bean resolution and result in `AmbiguousResolutionException` (not handled by the extension)
- Quarkus Easy NATS configuration properties (`quarkus.easynats.servers`, etc.) are the source of configuration for the default Options producer

## Dependencies & Constraints

- Depends on: NATS client library (`io.nats:jnats`), Quarkus Arc (CDI)
- Must work with Quarkus 3.27.0 LTS and Java 21
- Must support both JVM and native image builds
- No breaking changes to existing extension APIs or configuration
