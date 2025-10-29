package org.mjelle.quarkus.easynats.runtime.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Kubernetes liveness probe for NATS connection.
 *
 * The liveness probe indicates whether the application is running. It returns:
 * - UP if the NATS connection is CONNECTED, RECONNECTING, RECONNECTED, RESUBSCRIBED, or in LAME_DUCK state
 * - DOWN only if the connection is CLOSED (permanently)
 *
 * The liveness probe does not report DOWN during temporary disconnections (DISCONNECTED state)
 * because the application itself is still running and may be attempting to reconnect.
 */
@Liveness
@ApplicationScoped
public class NatsHealthCheck implements HealthCheck {

    private final ConnectionStatusHolder statusHolder;

    public NatsHealthCheck(ConnectionStatusHolder statusHolder) {
        this.statusHolder = statusHolder;
    }

    @Override
    public HealthCheckResponse call() {
        ConnectionStatus status = statusHolder.getStatus();

        // Liveness probe: Report DOWN only if connection is permanently CLOSED
        if (status == ConnectionStatus.CLOSED) {
            return HealthCheckResponse
                    .named("NATS Connection (Liveness)")
                    .down()
                    .withData("connectionStatus", status.name())
                    .build();
        }

        // All other states (including LAME_DUCK) are considered alive
        return HealthCheckResponse
                .named("NATS Connection (Liveness)")
                .up()
                .withData("connectionStatus", status.name())
                .build();
    }
}
