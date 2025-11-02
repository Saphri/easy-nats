package org.mjelle.quarkus.easynats.runtime;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.annotation.Priority;

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConfigurationException;
import org.mjelle.quarkus.easynats.NatsConnection;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatus;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;

import java.io.IOException;

/**
 * Provider that creates and manages the lifecycle of NatsConnection instances.
 * This is a singleton CDI bean that creates the NATS connection based on configuration.
 * <p>
 * The provider validates configuration at runtime and creates a wrapped connection
 * that delegates to the underlying NATS connection but provides a safe close() method.
 */
@ApplicationScoped
public class NatsConnectionProvider {

    private final Logger log = Logger.getLogger(NatsConnectionProvider.class);

    private final Options options;
    private final ConnectionStatusHolder statusHolder;
    private Connection natsConnection;
    private NatsConnection wrappedConnection;

    /**
     * Constructor injection of Options and status holder.
     * <p>
     * The Options are injected from the CDI container, which provides either:
     * - Default Options from NatsConnectionProducer (if no custom bean exists)
     * - Custom Options from a developer-provided bean (if @Produces @Unremovable bean exists)
     * <p>
     * The Options object is fully configured and includes:
     * - Server URLs and authentication (from NatsConnectionProducer)
     * - Executor service for async operations (added by producer)
     * - Connection listener for health checks (added by producer)
     * - SSL/TLS configuration (added by producer)
     * <p>
     * This follows the CDI @DefaultBean pattern: developers can completely override
     * the default Options by providing their own unqualified Options bean.
     *
     * @param options      the fully-configured NATS Options (injected from CDI container)
     * @param statusHolder the connection status holder
     */
    public NatsConnectionProvider(
            Options options,
            ConnectionStatusHolder statusHolder
    ) {
        this.options = options;
        this.statusHolder = statusHolder;
    }

    /**
     * Produces a singleton NatsConnection bean that can be injected throughout the application.
     * <p>
     * The connection is created during application startup via onStartup() method.
     * This producer simply returns the pre-created connection.
     *
     * @return a wrapped NATS connection
     * @throws NatsConfigurationException if configuration is invalid or connection fails
     */
    @Produces
    @Singleton
    public NatsConnection produceConnection() {
        if (wrappedConnection == null) {
            createConnection();
        }
        return wrappedConnection;
    }

    /**
     * Ensures the NATS connection is created early during application startup.
     * Runs first (priority 1) to ensure connection is ready for all other startup tasks.
     *
     * @param startupEvent the startup event
     */
    void onStartup(@Observes @Priority(1) StartupEvent startupEvent) {
        log.info("NatsConnectionProvider: Initializing connection on startup (priority 1 - runs first)");
        try {
            produceConnection();
            log.info("NatsConnectionProvider: Connection established at startup");
        } catch (Exception e) {
            log.errorf(e, "NatsConnectionProvider: Failed to establish connection at startup");
            throw new RuntimeException("Failed to establish NATS connection at startup", e);
        }
    }

    /**
     * Gracefully closes the NATS connection on application shutdown.
     *
     * @param shutdownEvent the shutdown event
     */
    void onShutdown(@Observes @Priority(100) ShutdownEvent shutdownEvent) {
        if (natsConnection != null && natsConnection.getStatus() != Connection.Status.CLOSED) {
            try {
                log.info("Closing NATS connection on application shutdown");
                natsConnection.close();
                statusHolder.setStatus(ConnectionStatus.CLOSED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warnf(e, "Connection close was interrupted");
            }
        }
    }

    /**
     * Internal method to create a NatsConnection wrapper around a new NATS connection.
     * <p>
     * This method uses fully-configured Options (injected via CDI) to establish the connection.
     * The Options are created by either:
     * - Default NatsConnectionProducer (from configuration properties and runtime settings)
     * - Custom developer-provided bean (if present, overrides default)
     * <p>
     * The connection is wrapped in a NatsConnection facade that prevents accidental closure.
     *
     * @return a wrapped NATS connection
     * @throws RuntimeException if connection fails
     */
    NatsConnection createConnection() {
        log.info("NatsConnectionProvider: Creating NATS connection with injected Options");

        try {
            // Connect to NATS using the fully-configured Options
            // The Options includes servers, authentication, executor service,
            // connection listener, and TLS configuration (all set by the producer)
            this.natsConnection = Nats.connect(options);
            statusHolder.setStatus(ConnectionStatus.CONNECTED);

            // Validate connection
            if (natsConnection == null) {
                throw new NatsConfigurationException("Failed to create NATS connection: connection is null");
            }

            if (natsConnection.getStatus() == Connection.Status.CLOSED) {
                throw new NatsConfigurationException(
                        "NATS connection is closed immediately after creation. Check server availability."
                );
            }

            // Wrap connection in facade
            this.wrappedConnection = new NatsConnection(natsConnection);

            log.infof("Successfully established NATS connection to: %s", natsConnection.getConnectedUrl());

            return wrappedConnection;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            statusHolder.setStatus(ConnectionStatus.DISCONNECTED);

            // Fail fast - application cannot function without NATS
            throw new RuntimeException(
                    "Failed to connect to NATS broker at startup. " +
                            "Application requires NATS connection to function. " +
                            "Ensure NATS server is running and Options configuration is correct.", e);
        }
    }

    /**
     * Returns the wrapped connection if already created, or null if not initialized.
     * This is for internal testing purposes.
     *
     * @return the wrapped connection, or null if not created yet
     */
    NatsConnection getWrappedConnection() {
        return wrappedConnection;
    }
}
