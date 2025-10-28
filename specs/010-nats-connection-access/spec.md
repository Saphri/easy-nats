# Feature Specification: NATS Connection Access API

**Feature Branch**: `010-nats-connection-access`
**Created**: 2025-10-28
**Status**: Draft
**Input**: User description: "as a developer I want easy and safe access to a connection to nats for any advanced use"

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

### User Story 1 - Access Raw NATS Connection for Advanced Operations (Priority: P1)

A developer using the Quarkus EasyNATS extension needs direct access to the underlying NATS connection to perform advanced operations not covered by the extension's higher-level APIs. Examples include creating custom subscriptions, managing push subscriptions, or accessing NATS metadata.

**Why this priority**: This is the core requirement that enables power users and advanced use cases. Without it, developers are blocked from using NATS features beyond what the extension provides. This is the minimum viable feature that delivers direct value.

**Independent Test**: Can be tested by verifying that a developer can obtain a NATS connection object, establish a subscription, and receive messages through it independently of the extension's publisher/subscriber annotations.

**Acceptance Scenarios**:

1. **Given** a Quarkus application with the EasyNATS extension enabled, **When** a developer injects or accesses a NATS connection, **Then** they receive a valid, open connection to the NATS server that can be used for any NATS operations.
2. **Given** an active NATS connection obtained from the extension, **When** the developer uses it to publish a message to a subject, **Then** the message is successfully delivered to all subscribers.
3. **Given** an active NATS connection obtained from the extension, **When** the developer creates a subscription and publishes a message, **Then** the subscription receives the message without delay.

---

### User Story 2 - Thread-Safe Connection Access (Priority: P1)

A developer working in a multi-threaded Quarkus application needs to safely access the NATS connection from multiple threads without race conditions or connection corruption.

**Why this priority**: Thread safety is critical for production applications. If the connection is not thread-safe, developers will encounter hard-to-debug concurrency issues that break in production. This is equally critical as basic access.

**Independent Test**: Can be tested by verifying that multiple concurrent threads can safely publish messages through the same connection and receive them correctly, with no connection errors or lost messages.

**Acceptance Scenarios**:

1. **Given** multiple threads accessing the same NATS connection simultaneously, **When** each thread publishes a message concurrently, **Then** all messages are delivered successfully without connection errors.
2. **Given** a connection being used by one thread for publishing while another thread subscribes, **When** both operations occur concurrently, **Then** the subscriber receives all published messages correctly.

---

### User Story 3 - Safe Connection Lifecycle Management (Priority: P2)

A developer needs confidence that the NATS connection is properly managed - it won't be prematurely closed while they're using it, and it will be cleaned up appropriately when the application shuts down.

**Why this priority**: This prevents resource leaks and connection errors. It's less critical than basic access and thread-safety, but essential for production stability. Without it, developers must manually manage connection lifecycle, introducing error-prone code.

**Independent Test**: Can be tested by verifying that the connection remains open during application runtime, and verifying that it is properly closed when the application shuts down without leaving zombie connections.

**Acceptance Scenarios**:

1. **Given** an application with an active NATS connection, **When** the connection is obtained for use, **Then** the connection remains open and usable until the application explicitly stops.
2. **Given** an application that has obtained a NATS connection, **When** the application shuts down, **Then** the connection is gracefully closed without leaving orphaned resources.

---

### User Story 4 - CDI Injection for Connection Access (Priority: P1)

A developer wants to obtain the NATS connection using standard CDI (Contexts and Dependency Injection) mechanisms, following Quarkus and Java best practices. This includes constructor injection, which is the standard pattern for the extension.

**Why this priority**: CDI injection is the idiomatic way to access managed beans in Quarkus applications. It's a critical requirement because it integrates the connection seamlessly with the application's dependency graph and lifecycle management. This is equally important as basic access since it defines how developers will actually use the feature.

**Independent Test**: Can be tested by verifying that a developer can inject a NATS connection into a bean using constructor injection, and that the injected connection is the same instance used by the extension's publishers and subscribers.

**Acceptance Scenarios**:

1. **Given** a developer using constructor injection in a Quarkus bean, **When** they declare a constructor parameter of type `Connection`, **Then** the extension provides the active NATS connection without additional configuration.
2. **Given** a developer injecting the connection in multiple beans, **When** each bean injects the connection, **Then** all beans receive the same underlying connection instance.
3. **Given** a Quarkus application with the extension enabled, **When** the application starts up, **Then** the connection is properly initialized and ready for injection before any beans are instantiated.

---

### User Story 5 - Try-with-Resources Support for Safe Scoped Usage (Priority: P2)

A developer wants to use Java's try-with-resources statement when working with the NATS connection for advanced operations, to ensure proper resource management and clean code practices.

**Why this priority**: Try-with-resources is a standard Java idiom for resource management. Supporting it improves code readability and developer familiarity. It's secondary to basic access since developers can still use the connection without it, but important for ergonomics.

**Independent Test**: Can be tested by verifying that a developer can successfully use the connection in a try-with-resources block, with no compilation errors, and that the connection remains open and usable by other parts of the application after the try block exits.

**Acceptance Scenarios**:

1. **Given** a developer using try-with-resources syntax, **When** they obtain a NATS connection wrapper in a try statement, **Then** the code compiles and executes successfully.
2. **Given** a developer using try-with-resources with the connection, **When** the try block exits normally, **Then** the underlying connection remains open for use by other application components.
3. **Given** a developer using try-with-resources with the connection, **When** an exception is thrown in the try block, **Then** the underlying connection remains open and available for recovery or cleanup operations.

---

### User Story 6 - Configuration via Environment Variables and application.properties (Priority: P1)

A developer wants to configure the NATS connection using standard Quarkus configuration mechanisms (environment variables and application.properties file), including NATS server addresses, authentication credentials, and SSL settings.

**Why this priority**: Configuration of the NATS connection is fundamental and critical. Without it, developers cannot connect to different NATS servers for different environments (dev, staging, prod). This is essential for any production application and is equally critical as establishing the connection itself.

**Independent Test**: Can be tested by verifying that a developer can configure a NATS connection using environment variables, that the same configuration works via application.properties, and that the configured servers/credentials are actually used when establishing the connection.

**Acceptance Scenarios**:

1. **Given** a developer configures NATS servers via environment variable `QUARKUS_EASYNATS_SERVERS`, **When** the application starts, **Then** the connection uses the configured servers.
2. **Given** a developer configures authentication via `QUARKUS_EASYNATS_USERNAME` and `QUARKUS_EASYNATS_PASSWORD`, **When** the application connects to NATS, **Then** the connection authenticates using the provided credentials.
3. **Given** a developer configures SSL via `QUARKUS_EASYNATS_SSL_ENABLED=true`, **When** the application connects to NATS, **Then** the connection uses SSL/TLS for the transport (if available for testing).
4. **Given** a developer uses application.properties file with `quarkus.easynats.servers`, **When** the application starts, **Then** the configuration is read and applied the same way as environment variables.
5. **Given** multiple configuration sources (env vars override properties file), **When** both are defined, **Then** environment variables take precedence as per Quarkus standard configuration precedence.

---

### Edge Cases

- What happens if a developer tries to access the connection before the application is fully initialized?
- How does the extension handle connection failures or reconnections while a developer is using the connection?
- What mechanisms prevent a developer from accidentally closing the connection and breaking other subscriptions/publishers?
- How does the feature handle multiple Quarkus applications running in the same JVM process?
- What happens if no servers are configured or the configuration is empty?
- How does the extension handle invalid server addresses (malformed URLs, non-existent hosts)?
- What happens if authentication credentials are incomplete (username without password)?
- How does the extension handle SSL configuration when the NATS server doesn't support SSL?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: Extension MUST register the NATS connection as a CDI bean in the Quarkus application context
- **FR-002**: Developers MUST be able to inject the connection using standard CDI constructor injection patterns
- **FR-003**: The connection provided MUST be the same underlying NATS connection used by the extension's publisher and subscriber mechanisms
- **FR-004**: The extension MUST ensure the connection is fully initialized and available before any application beans are instantiated
- **FR-005**: All beans that inject the connection MUST receive the same singleton instance
- **FR-006**: The connection MUST be thread-safe for concurrent use by multiple threads
- **FR-007**: The connection MUST remain valid and usable for the entire application lifetime
- **FR-008**: The extension MUST prevent developers from closing the connection, since the connection is shared by all application publishers and subscribers
- **FR-009**: The connection provided to developers MUST NOT expose a `close()` method or equivalent that could terminate the shared connection
- **FR-010**: If a developer attempts to call `close()` on the provided connection, the extension MUST prevent the operation and provide a clear error message explaining that closing is not permitted
- **FR-011**: Developers MUST receive clear error messages if they attempt to use a closed connection (in case the connection is closed by the extension due to connection failures)
- **FR-012**: Extension MUST provide a connection wrapper that implements `AutoCloseable` to support try-with-resources usage
- **FR-013**: When a developer uses the connection wrapper in a try-with-resources statement, the underlying NATS connection MUST NOT be closed when the try block exits
- **FR-014**: The `close()` method on the connection wrapper MUST be a safe no-op that does not affect the underlying connection or other application components
- **FR-015**: The connection wrapper MUST transparently delegate all NATS operations to the underlying connection (no performance degradation)
- **FR-016**: Extension MUST provide documentation showing common advanced use cases (custom subscriptions, push subscriptions, metadata access), try-with-resources examples, and explicitly warn against attempting to close the connection
- **FR-017**: Extension MUST support configuration of NATS servers via property `quarkus.easynats.servers` in application.properties
- **FR-018**: Extension MUST support configuration of NATS servers via environment variable `QUARKUS_EASYNATS_SERVERS`
- **FR-019**: Extension MUST support configuration of authentication username via property `quarkus.easynats.username` and environment variable `QUARKUS_EASYNATS_USERNAME`
- **FR-020**: Extension MUST support configuration of authentication password via property `quarkus.easynats.password` and environment variable `QUARKUS_EASYNATS_PASSWORD`
- **FR-021**: Extension MUST support configuration of SSL/TLS via property `quarkus.easynats.ssl-enabled` and environment variable `QUARKUS_EASYNATS_SSL_ENABLED`
- **FR-022**: Configuration via environment variables MUST take precedence over application.properties, following standard Quarkus configuration precedence rules
- **FR-023**: The extension MUST apply all configured settings when establishing the connection to the NATS server
- **FR-024**: The extension MUST provide clear error messages if NATS server connection fails due to configuration errors (invalid servers, authentication failure, etc.)
- **FR-025**: The extension MUST provide configuration documentation including examples for all configurable properties

### Key Entities

- **NATS Connection**: The underlying `io.nats.client.Connection` object from the NATS client library, representing an open connection to the NATS server.
- **Connection Wrapper**: A safe wrapper around the NATS connection that implements `AutoCloseable` for try-with-resources support, with a no-op `close()` method to prevent accidental connection closure.
- **CDI Bean**: The extension registers the connection wrapper as a singleton CDI bean in the Quarkus application context, enabling standard dependency injection.
- **Connection Manager**: The extension component (Quarkus build-time processor) responsible for registering the connection as a CDI bean and managing its lifecycle.
- **Configuration**: Quarkus-managed configuration properties and environment variables that control NATS connection parameters (servers, credentials, SSL settings). Follows standard Quarkus configuration precedence.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Developers can obtain a NATS connection and successfully execute any NATS client operation within 30 seconds of reading the documentation.
- **SC-002**: Connection access works reliably in multi-threaded scenarios with zero message loss or connection corruption under concurrent load.
- **SC-003**: 95% of developers attempting to use advanced NATS operations report that the API is intuitive and required minimal trial-and-error.
- **SC-004**: Zero connection-related resource leaks detected in applications using the access API over extended runtime periods.
- **SC-005**: Connection operations complete with minimal latency overhead (no more than 5% additional latency compared to direct NATS client access).
- **SC-006**: Try-with-resources usage is fully supported with zero unintended side effects (underlying connection remains open and functional after try block exits).
- **SC-007**: Developers can configure a complete NATS connection (servers, authentication, SSL) using only environment variables or application.properties within 5 minutes of reading the documentation.
- **SC-008**: Configuration via both environment variables and application.properties works identically, with environment variables correctly overriding property file settings.
- **SC-009**: 100% of configuration errors (invalid servers, authentication failures, missing required settings) result in clear, actionable error messages during application startup.

## Assumptions

1. **CDI is the standard access mechanism**: Developers access the connection exclusively through CDI constructor injection. This follows Quarkus and Java best practices and integrates seamlessly with the application's lifecycle management.
2. **Singleton bean scope**: The connection is registered as a singleton CDI bean, ensuring all beans receive the same instance throughout the application lifetime.
3. **NATS client thread safety**: We assume the underlying `io.nats:jnats` library is thread-safe for the connection object, as documented in the NATS Java client.
4. **Single NATS connection per application**: We assume a typical Quarkus application uses a single NATS connection. Multiple connections per application are not explicitly supported in this feature.
5. **Shared connection is critical**: The connection is a shared resource used by all application publishers and subscribers. Closing it would break all NATS communication in the application. Therefore, developers must not be able to close the connection. The try-with-resources pattern is supported via a safe wrapper with a no-op `close()` method.
6. **Wrapper pattern for AutoCloseable**: We provide a thin wrapper that implements `AutoCloseable` but does not close the underlying connection. This allows developers to use try-with-resources idiomatically while maintaining safety.
7. **Quarkus and Arc availability**: We assume Quarkus 3.27.0+ and Arc (CDI) are available and properly configured in the runtime module, as these are core dependencies of the extension.
8. **Quarkus MicroProfile Config**: We assume Quarkus's implementation of MicroProfile Config API is available for reading configuration from environment variables and properties files, following standard Quarkus precedence rules.
9. **Configuration naming convention**: Configuration properties follow Quarkus naming conventions (`quarkus.easynats.*`) and environment variable conventions (`QUARKUS_EASYNATS_*`), automatically converted by the Quarkus framework.
10. **Server list format**: Multiple NATS servers are provided as comma-separated values (e.g., `nats://host1:4222,nats://host2:4222`), a standard format for NATS client libraries.
11. **SSL support limitation**: SSL/TLS configuration is supported in the extension, but the project acknowledges that SSL testing cannot be performed in the current development environment. SSL configuration code paths must still be implemented correctly and documented.
12. **Basic NATS knowledge**: We assume developers accessing the raw connection have basic familiarity with the NATS client API, even though we'll provide examples.

## Out of Scope

- **Connection pooling or multiple connections**: This feature provides access to a single application-level connection.
- **Non-CDI access patterns**: Static factory methods, service locators, or other non-CDI mechanisms are explicitly not supported. CDI injection is the only supported access pattern.
- **Custom NATS server configuration**: We assume the NATS server is already configured and accessible; this feature doesn't configure NATS itself.
- **Advanced NATS connection options**: Advanced NATS client features beyond servers, authentication (username/password), and SSL are out of scope (e.g., custom authentication handlers, advanced TLS options, connection timeout tuning). These can be added as future enhancements.
- **SSL testing**: The feature supports SSL/TLS configuration, but testing is out of scope due to environment limitations. Implementation must still be correct and properly documented.
