package org.mjelle.quarkus.easynats.runtime;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.quarkus.arc.Arc;
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
import org.mjelle.quarkus.easynats.runtime.health.NatsConnectionListener;
import org.mjelle.quarkus.easynats.runtime.startup.SubscriberInitializer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import io.quarkus.tls.TlsConfigurationRegistry;
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
    private final TlsConfigurationRegistry tlsRegistry;
    private final ConnectionStatusHolder statusHolder;
    private Connection natsConnection;
    private NatsConnection wrappedConnection;

    /**
     * Constructor injection of configuration, executor service, and TLS registry.
     *
     * @param config          the NATS configuration
     * @param executorService the virtual thread executor service
     * @param tlsRegistry     the Quarkus TLS configuration registry
     */
    public NatsConnectionProvider(
            NatsConfiguration config,
            @VirtualThreads ExecutorService executorService,
            TlsConfigurationRegistry tlsRegistry,
            ConnectionStatusHolder statusHolder
    ) {
        this.config = config;
        this.executorService = executorService;
        this.tlsRegistry = tlsRegistry;
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
     * This method validates the configuration and establishes a connection to the NATS server.
     * The connection is wrapped in a NatsConnection facade that prevents accidental closure.
     *
     * @return a wrapped NATS connection
     * @throws NatsConfigurationException if configuration is invalid or connection fails
     */
    NatsConnection createConnection() {
        // Log what configuration is actually available
        log.infof("NatsConnectionProvider: Creating connection. Servers present: %s, Servers value: %s",
                config.servers().isPresent(),
                config.servers().map(list -> list.isEmpty() ? "[empty list]" : String.join(", ", list))
                        .orElse("[not present]"));

        // Validate configuration first
        try {
            config.validate();
        } catch (NatsConfigurationException e) {
            log.errorf(e, "Invalid NATS configuration");
            throw e;
        }

        // Verify servers are available (they should be provided by Dev Services or explicit config by now)
        if (config.servers().isEmpty() || config.servers().get() == null || config.servers().get().isEmpty()) {
            throw new NatsConfigurationException(
                    "NATS servers configuration is empty. " +
                    "Ensure Dev Services is enabled or set 'quarkus.easynats.servers' property explicitly"
            );
        }

        try {
            // Build connection options
            Options.Builder optionsBuilder = new Options.Builder();

            // Add servers (guaranteed to exist after checks above)
            String[] serverUrls = config.servers().get().toArray(new String[0]);
            optionsBuilder.servers(serverUrls);

            // Add authentication if configured
            if (config.username().isPresent() && config.password().isPresent()) {
                optionsBuilder.userInfo(config.username().get(), config.password().get());
            }

            // Add executor service for async operations
            optionsBuilder.executor(executorService);

            // Add connection listener for health checks
            optionsBuilder.connectionListener(new NatsConnectionListener(statusHolder));

            // Always set the SSLContext if a TLS configuration is available in Quarkus.
            // The jnats client will only use it if the server URL has a TLS scheme (tls://, wss://).
            if (config.sslEnabled()) {
                var tlsConfiguration = config.tlsConfigurationName()
                        .flatMap(tlsRegistry::get)
                        .or(tlsRegistry::getDefault);

                tlsConfiguration.ifPresent(cfg -> {
                    try {
                        optionsBuilder.sslContext(cfg.createSSLContext());
                        log.infof("Configured TLS for NATS connection using: %s",
                                config.tlsConfigurationName().orElse("default"));
                    } catch (Exception e) {
                        throw new NatsConfigurationException(
                                "Failed to create SSLContext from TLS configuration: " +
                                        config.tlsConfigurationName().orElse("default"), e);
                    }
                });
            }

            Options options = optionsBuilder.build();

            // Connect to NATS
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

            String servers = String.join(", ", sanitizeUrls(config.servers().get()));
            log.infof("Successfully connected to NATS server(s): %s", servers);
            log.infof("Connected to: %s", natsConnection.getConnectedUrl());

            return wrappedConnection;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            statusHolder.setStatus(ConnectionStatus.DISCONNECTED);

            // Fail fast - application cannot function without NATS
            String servers = String.join(", ", sanitizeUrls(config.servers().get()));
            throw new RuntimeException(
                    "Failed to connect to NATS broker(s) at startup: " + servers + ". " +
                            "Application requires NATS connection to function. " +
                            "Ensure NATS server is running and configuration is correct.", e);
        }
    }

    /**
     * Sanitizes URLs by removing embedded credentials for logging.
     * Prevents credentials from appearing in logs.
     *
     * @param urls list of URLs to sanitize
     * @return list of sanitized URLs
     */
    private java.util.List<String> sanitizeUrls(java.util.List<String> urls) {
        return urls.stream()
                .map(url -> url.replaceAll("://[^:]+:[^@]+@", "://***:***@"))
                .toList();
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
