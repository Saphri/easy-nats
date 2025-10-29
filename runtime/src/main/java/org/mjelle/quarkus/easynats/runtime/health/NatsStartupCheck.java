package org.mjelle.quarkus.easynats.runtime.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

/**
 * Kubernetes startup probe for NATS connection.
 *
 * The startup probe indicates whether the application has completed its initialization.
 * It returns:
 * - UP if the NATS connection is CONNECTED, RECONNECTED, or RESUBSCRIBED
 * - DOWN if the connection is DISCONNECTED, RECONNECTING, CLOSED, or in LAME_DUCK state
 *
 * The startup probe has the same logic as the readiness probe, ensuring that the
 * application reports success only when it is fully initialized and ready to process messages.
 */
@Startup
@ApplicationScoped
public class NatsStartupCheck implements HealthCheck {

    private final ConnectionStatusHolder statusHolder;

    public NatsStartupCheck(ConnectionStatusHolder statusHolder) {
        this.statusHolder = statusHolder;
    }

    @Override
    public HealthCheckResponse call() {
        ConnectionStatus status = statusHolder.getStatus();

        // Startup probe: Report UP only for fully connected states
        if (status == ConnectionStatus.CONNECTED ||
                status == ConnectionStatus.RECONNECTED ||
                status == ConnectionStatus.RESUBSCRIBED) {
            return HealthCheckResponse
                    .named("NATS Connection (Startup)")
                    .up()
                    .withData("connectionStatus", status.name())
                    .build();
        }

        // All other states (DISCONNECTED, RECONNECTING, CLOSED, LAME_DUCK) mean not ready
        return HealthCheckResponse
                .named("NATS Connection (Startup)")
                .down()
                .withData("connectionStatus", status.name())
                .build();
    }
}
