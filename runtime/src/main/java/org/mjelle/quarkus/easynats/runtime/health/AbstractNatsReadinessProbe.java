package org.mjelle.quarkus.easynats.runtime.health;

import java.util.Set;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * Abstract base class for readiness-based health probes.
 *
 * This class encapsulates the common logic for checks that report UP only when
 * the NATS connection is in a fully connected state (CONNECTED, RECONNECTED, RESUBSCRIBED).
 * All other states (including temporary disconnections and graceful shutdown) report DOWN.
 *
 * Concrete implementations (e.g., readiness and startup probes) inherit this logic
 * and only need to provide their specific health check name.
 */
public abstract class AbstractNatsReadinessProbe implements HealthCheck {

    private static final Set<ConnectionStatus> HEALTHY_STATES = Set.of(
            ConnectionStatus.CONNECTED,
            ConnectionStatus.RECONNECTED,
            ConnectionStatus.RESUBSCRIBED
    );

    private final ConnectionStatusHolder statusHolder;
    private final String checkName;

    /**
     * Constructor for subclasses.
     *
     * @param statusHolder the bean holding the current NATS connection status
     * @param checkName    the name of this health check (e.g., "NATS Connection (Readiness)")
     */
    protected AbstractNatsReadinessProbe(ConnectionStatusHolder statusHolder, String checkName) {
        this.statusHolder = statusHolder;
        this.checkName = checkName;
    }

    @Override
    public HealthCheckResponse call() {
        ConnectionStatus status = statusHolder.getStatus();

        // Report UP only for fully connected states
        if (HEALTHY_STATES.contains(status)) {
            return HealthCheckResponse
                    .named(checkName)
                    .up()
                    .withData("connectionStatus", status.name())
                    .build();
        }

        // All other states (DISCONNECTED, CLOSED, LAME_DUCK) report DOWN
        return HealthCheckResponse
                .named(checkName)
                .down()
                .withData("connectionStatus", status.name())
                .build();
    }
}
