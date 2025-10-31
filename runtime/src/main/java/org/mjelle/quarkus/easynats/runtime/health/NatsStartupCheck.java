package org.mjelle.quarkus.easynats.runtime.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kubernetes startup probe for NATS connection.
 *
 * The startup probe indicates whether the application has completed its initialization.
 * Once the NATS connection is established for the first time, this probe will report "UP"
 * and will continue to do so for the life of the application. This "latching" behavior
 * prevents the application from being terminated if the NATS connection is lost after a
 * successful startup.
 *
 * It returns:
 * - UP if the NATS connection has been established at least once.
 * - DOWN if the NATS connection has not yet been established.
 */
@Startup
@ApplicationScoped
public class NatsStartupCheck extends AbstractNatsReadinessProbe {

    private final AtomicBoolean isReady = new AtomicBoolean(false);

    public NatsStartupCheck() {
        super(null, "NATS Connection (Startup)");
    }

    @Inject
    public NatsStartupCheck(ConnectionStatusHolder statusHolder) {
        super(statusHolder, "NATS Connection (Startup)");
    }

    @Override
    public HealthCheckResponse call() {
        if (isReady.get()) {
            return HealthCheckResponse.named("NATS Connection (Startup)").up().build();
        }

        HealthCheckResponse response = super.call();
        if (response.getStatus() == HealthCheckResponse.Status.UP) {
            isReady.set(true);
        }

        return response;
    }
}
