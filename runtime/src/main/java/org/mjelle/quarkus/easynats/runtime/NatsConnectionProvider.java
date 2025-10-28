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

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConfigurationException;
import org.mjelle.quarkus.easynats.NatsConnection;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;

import io.quarkus.virtual.threads.VirtualThreads;

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

    private final NatsConfiguration config;
    private final ExecutorService executorService;
    private Connection natsConnection;
    private NatsConnection wrappedConnection;

    /**
     * Constructor injection of configuration and executor service.
     *
     * @param config          the NATS configuration
     * @param executorService the virtual thread executor service
     */
    public NatsConnectionProvider(
            NatsConfiguration config,
            @VirtualThreads ExecutorService executorService
    ) {
        this.config = config;
        this.executorService = executorService;
    }

    /**
     * Initializes the NATS connection on application startup.
     * This ensures the connection is established early in the application lifecycle.
     *
     * @param startupEvent the startup event
     */
    void onStartup(@Observes StartupEvent startupEvent) {
        if (wrappedConnection == null) {
            createConnection();
        }
    }

    /**
     * Produces a singleton NatsConnection bean that can be injected throughout the application.
     * <p>
     * This producer method is called by the CDI container when NatsConnection is requested.
     * The connection is created on first access and reused for all subsequent injections.
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
     * Internal method to create a NatsConnection wrapper around a new NATS connection.
     * <p>
     * This method validates the configuration and establishes a connection to the NATS server.
     * The connection is wrapped in a NatsConnection facade that prevents accidental closure.
     *
     * @return a wrapped NATS connection
     * @throws NatsConfigurationException if configuration is invalid or connection fails
     */
    NatsConnection createConnection() {
        // Validate configuration first
        try {
            config.validate();
        } catch (NatsConfigurationException e) {
            log.errorf(e, "Invalid NATS configuration");
            throw e;
        }

        try {
            // Build connection options
            Options.Builder optionsBuilder = new Options.Builder();

            // Add servers
            String[] serverUrls = config.servers().toArray(new String[0]);
            optionsBuilder.servers(serverUrls);

            // Add authentication if configured
            if (config.username().isPresent() && config.password().isPresent()) {
                optionsBuilder.userInfo(config.username().get(), config.password().get());
            }

            // Add executor service for async operations
            optionsBuilder.executor(executorService);

            // Note: SSL context would be added here when sslEnabled is true
            // For now, we rely on the NATS client's default SSL handling based on URL scheme (nats:// vs tls://)
            // Add authentication if configured
            if (config.sslEnabled()) {
                try {
                    optionsBuilder.sslContext(SSLContext.getDefault());
                } catch (NoSuchAlgorithmException e) {
                    throw new NatsConfigurationException("Failed to create SSL context", e);
                }
            }


            Options options = optionsBuilder.build();

            // Connect to NATS
            this.natsConnection = Nats.connect(options);

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

            log.infof("Successfully connected to NATS server(s): %s", String.join(", ", config.servers()));
            log.infof("Connected to: %s", natsConnection.getConnectedUrl());

            return wrappedConnection;

        } catch (IOException e) {
            String errorMsg = "Failed to connect to NATS server(s): " + String.join(", ", config.servers());
            log.errorf(e, errorMsg);
            throw new NatsConfigurationException(errorMsg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "Connection to NATS was interrupted";
            log.errorf(e, errorMsg);
            throw new NatsConfigurationException(errorMsg, e);
        }
    }

    /**
     * Gracefully closes the NATS connection on application shutdown.
     *
     * @param shutdownEvent the shutdown event
     */
    void onShutdown(@Observes ShutdownEvent shutdownEvent) {
        if (natsConnection != null && natsConnection.getStatus() != Connection.Status.CLOSED) {
            try {
                log.info("Closing NATS connection on application shutdown");
                natsConnection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warnf(e, "Connection close was interrupted");
            }
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
