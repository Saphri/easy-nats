# Feature Specification: NATS Health Check Endpoints

**Feature Branch**: `011-nats-health-endpoints`
**Continues**: `010-nats-connection-access`
**Created**: 2025-10-29
**Status**: Draft
**Input**: User description: "This feature introduces standard health check endpoints (liveness, readiness, and startup) to expose the status of the NATS connection. These endpoints are essential for container orchestration systems like Kubernetes to manage the application's lifecycle effectively, ensuring pods are only sent traffic when ready and are restarted if they become unresponsive."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Kubernetes Liveness Probe (Priority: P1)

A container orchestration system (like Kubernetes) needs to determine if the application is running correctly. It will periodically call the liveness endpoint. If the NATS connection is lost and cannot be re-established, the application should be considered unhealthy and restarted.

**Why this priority**: Prevents the application from running in a zombie state where it's running but cannot process messages, ensuring high availability.

**Independent Test**: The liveness endpoint can be called independently. If the NATS connection is active, it returns a success status. If the connection is down, it returns a failure status.

**Acceptance Scenarios**:

1.  **Given** the application is running and connected to NATS, **When** the liveness endpoint is called, **Then** it returns an HTTP 200 OK status and a JSON response with `{"status": "UP", "checks": [{"name": "NATS Connection", "status": "UP", "data": {"connectionStatus": "CONNECTED"}}]}`.
2.  **Given** the application is running and the NATS connection is temporarily disconnected (reconnecting), **When** the liveness endpoint is called, **Then** it returns an HTTP 200 OK status and a JSON response with `{"status": "UP", "checks": [{"name": "NATS Connection", "status": "UP", "data": {"connectionStatus": "RECONNECTING"}}]}`.
3.  **Given** the application is running and the NATS connection is permanently closed, **When** the liveness endpoint is called, **Then** it returns an HTTP 503 Service Unavailable status and a JSON response with `{"status": "DOWN", "checks": [{"name": "NATS Connection", "status": "DOWN", "data": {"connectionStatus": "CLOSED"}}]}`.

---

### User Story 2 - Kubernetes Readiness Probe (Priority: P1)

A container orchestration system needs to know when the application is ready to start accepting traffic. It will call the readiness endpoint after the application starts. The application is ready only when it has successfully established a connection to NATS.

**Why this priority**: Ensures that traffic is not routed to a pod that is not yet ready to process messages, preventing message loss and processing errors during startup or redeployment.

**Independent Test**: The readiness endpoint can be called at any time. It will return a success status only if the NATS connection is established.

**Acceptance Scenarios**:

1.  **Given** the application is starting up and not yet connected to NATS, **When** the readiness endpoint is called, **Then** it returns an HTTP 503 Service Unavailable status and a JSON response with `{"status": "DOWN", "checks": [{"name": "NATS Connection", "status": "DOWN", "data": {"connectionStatus": "DISCONNECTED"}}]}`.
2.  **Given** the application has successfully connected to NATS, **When** the readiness endpoint is called, **Then** it returns an HTTP 200 OK status and a JSON response with `{"status": "UP", "checks": [{"name": "NATS Connection", "status": "UP", "data": {"connectionStatus": "CONNECTED"}}]}`.
3.  **Given** the application was ready but the NATS connection is temporarily disconnected (reconnecting), **When** the readiness endpoint is called, **Then** it returns an HTTP 503 Service Unavailable status and a JSON response with `{"status": "DOWN", "checks": [{"name": "NATS Connection", "status": "DOWN", "data": {"connectionStatus": "RECONNECTING"}}]}`.
4.  **Given** the application has reconnected to NATS after a temporary disconnection, **When** the readiness endpoint is called, **Then** it returns an HTTP 200 OK status and a JSON response with `{"status": "UP", "checks": [{"name": "NATS Connection", "status": "UP", "data": {"connectionStatus": "RECONNECTED"}}]}`.

---

### User Story 3 - Kubernetes Startup Probe (Priority: P2)

For applications that may have a slow startup time, a container orchestration system can use a startup probe to allow for a longer initial startup period before switching to the liveness probe.

**Why this priority**: This is important for applications that might have complex initialization logic that needs to complete before the application is considered live. It prevents the application from being killed prematurely during startup.

**Independent Test**: The startup endpoint can be called independently. It will return a success status only if the NATS connection is established.

**Acceptance Scenarios**:

1.  **Given** the application is starting up and not yet connected to NATS, **When** the startup endpoint is called, **Then** it returns an HTTP 503 Service Unavailable status and a JSON response with `{"status": "DOWN", "checks": [{"name": "NATS Connection", "status": "DOWN", "data": {"connectionStatus": "DISCONNECTED"}}]}`.
2.  **Given** the application has successfully connected to NATS, **When** the startup endpoint is called, **Then** it returns an HTTP 200 OK status and a JSON response with `{"status": "UP", "checks": [{"name": "NATS Connection", "status": "UP", "data": {"connectionStatus": "CONNECTED"}}]}`.

### Edge Cases

-   How does the system handle a flapping NATS connection (connecting and disconnecting frequently)? The readiness and liveness probes should accurately reflect the current connection state.

## Requirements *(mandatory)*

### Functional Requirements

-   **FR-001**: The system MUST expose a liveness health check endpoint.
-   **FR-002**: The system MUST expose a readiness health check endpoint.
-   **FR-003**: The system MUST expose a startup health check endpoint.
-   **FR-004**: The liveness endpoint MUST return a success status if the application has an active NATS connection.
-   **FR-005**: The liveness endpoint MUST return a failure status if the NATS connection is lost.
-   **FR-006**: The readiness endpoint MUST return a success status only when the application has an active NATS connection.
-   **FR-007**: The readiness endpoint MUST return a failure status if the application is not connected to NATS.
-   **FR-008**: The startup endpoint MUST return a success status only when the application has an active NATS connection.
-   **FR-009**: The startup endpoint MUST return a failure status if the application is not connected to NATS.
-   **FR-010**: The health check endpoints MUST include detailed NATS connection status (e.g., `CONNECTED`, `DISCONNECTED`) in their JSON response.
-   **FR-011**: The liveness probe MUST remain `UP` during temporary NATS disconnections and only report `DOWN` when the connection is permanently `CLOSED`.
-   **FR-012**: The readiness probe MUST report `DOWN` as soon as the NATS connection is lost (`DISCONNECTED`) and only report `UP` after it has been successfully re-established (`RECONNECTED` or `RESUBSCRIBED`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

-   **SC-001**: When the NATS connection is healthy, the liveness, readiness, and startup endpoints respond with an HTTP 200 OK status and a JSON response with `{"status": "UP", "checks": [{"name": "NATS Connection", "status": "UP", "data": {"connectionStatus": "CONNECTED"}}]}` within 100ms.
-   **SC-002**: When the NATS connection is down, the liveness, readiness, and startup endpoints respond with an HTTP 503 Service Unavailable status and a JSON response with `{"status": "DOWN", "checks": [{"name": "NATS Connection", "status": "DOWN", "data": {"connectionStatus": "DISCONNECTED"}}]}` within 100ms.
-   **SC-003**: In a Kubernetes environment, a pod running the application is automatically restarted if its NATS connection is lost for a configurable period.
-   **SC-004**: In a Kubernetes environment, a new pod running the application does not receive traffic until its NATS connection is established.

## Clarifications

### Session 2025-10-29

-   Q: Should the timeout for the NATS connection check within the health probe be configurable, and if so, what should the default value be? → A: No, the health check should wait indefinitely.
-   Q: Should the health check response body include detailed information about the NATS connection status beyond the simple "UP" or "DOWN"? → A: Yes, include the NATS connection status (e.g., `CONNECTED`, `DISCONNECTED`).
-   Q: How should the readiness and liveness probes report the application's status while the NATS client is attempting to reconnect? → A: The readiness probe will report `DOWN` on a `DISCONNECTED` event and `UP` on `RECONNECTED`/`RESUBSCRIBED`. The liveness probe will only report `DOWN` on a `CLOSED` event.