# Research: NATS Health Check Implementation

## Decision

The health checks will be implemented using the standard Quarkus SmallRye Health extension. A custom `HealthCheck` bean will be created to report the NATS connection status.

## Rationale

-   **Quarkus Integration**: SmallRye Health is the standard way to implement health checks in Quarkus. It provides the necessary annotations (`@Liveness`, `@Readiness`) and integrates seamlessly with the Quarkus ecosystem, including native image compilation.
-   **Observability**: It automatically exposes the health check endpoints (`/q/health/live`, `/q/health/ready`, `/q/health/started`) that are expected by container orchestration platforms like Kubernetes.
-   **Developer Experience**: Using the standard extension means developers familiar with Quarkus will immediately understand how the health checks are implemented and how to interact with them.

## Alternatives Considered

-   **Custom JAX-RS Endpoint**: Creating a custom JAX-RS endpoint would provide more control over the response format, but it would require manual implementation of the logic that SmallRye Health provides out of the box. This would be reinventing the wheel and would not be as well-integrated with the Quarkus ecosystem.

## Connection State Tracking

### Decision

The NATS connection status will be tracked by registering a `io.nats.client.ConnectionListener` on the shared NATS connection. This listener will update an internal state holder that the `HealthCheck` bean will read. The state holder will differentiate between transient (`DISCONNECTED`, `RECONNECTING`) and permanent (`CLOSED`) connection failures, as well as graceful shutdown (`LAME_DUCK`).

### Rationale

-   **Efficiency**: This is an event-driven approach. The application reacts to connection events rather than actively polling or probing the connection, which is far more efficient.
-   **Accuracy**: It provides the most accurate and real-time status of the connection as reported by the underlying `jnats` client.
-   **Simplicity**: It avoids the complexity of implementing a reliable active probing mechanism.

### Alternatives Considered

-   **Active Probing**: The health check could actively perform an operation (e.g., a round-trip request to the server) to verify the connection. This is less efficient, adds latency to the health check, and puts unnecessary load on the NATS server.