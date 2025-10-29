package org.mjelle.quarkus.easynats.runtime.health;

import jakarta.enterprise.context.ApplicationScoped;
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
public class NatsStartupCheck extends AbstractNatsReadinessProbe {

    public NatsStartupCheck(ConnectionStatusHolder statusHolder) {
        super(statusHolder, "NATS Connection (Startup)");
    }
}
