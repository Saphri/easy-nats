package org.mjelle.quarkus.easynats.runtime.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.Readiness;

/**
 * Kubernetes readiness probe for NATS connection.
 *
 * The readiness probe indicates whether the application is ready to accept traffic.
 * It returns:
 * - UP if the NATS connection is CONNECTED, RECONNECTED, or RESUBSCRIBED
 * - DOWN if the connection is DISCONNECTED, RECONNECTING, CLOSED, or in LAME_DUCK state
 *
 * The readiness probe is stricter than the liveness probe because it ensures that
 * the application is not only alive, but also ready to process messages. During
 * temporary disconnections (DISCONNECTED, RECONNECTING), the application should not
 * receive traffic.
 */
@Readiness
@ApplicationScoped
public class NatsReadinessCheck extends AbstractNatsReadinessProbe {

    public NatsReadinessCheck(ConnectionStatusHolder statusHolder) {
        super(statusHolder, "NATS Connection (Readiness)");
    }
}
