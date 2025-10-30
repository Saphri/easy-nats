package org.mjelle.quarkus.easynats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.tls.TlsConfigurationRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import io.quarkus.virtual.threads.VirtualThreads;
import org.mjelle.quarkus.easynats.runtime.NatsConfiguration;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatus;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;
import org.mjelle.quarkus.easynats.runtime.health.NatsConnectionListener;

/**
 * Manages the singleton NATS JetStream connection for the entire application.
 *
 * Initialization: Connects to NATS broker on application startup using configuration from application.properties.
 * Shutdown: Gracefully closes the connection on application shutdown.
 */
@ApplicationScoped // Note: @ApplicationScoped provides singleton semantics in Quarkus
public class NatsConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(NatsConnectionManager.class);

    private final ExecutorService executorService;
    private final ConnectionStatusHolder statusHolder;
    private final NatsConfiguration config;
    private final TlsConfigurationRegistry tlsRegistry;

    private Connection connection;
    private JetStream jetStream;

    public NatsConnectionManager(
            @VirtualThreads ExecutorService executorService,
            ConnectionStatusHolder statusHolder,
            NatsConfiguration config,
            TlsConfigurationRegistry tlsRegistry) {
        this.executorService = executorService;
        this.statusHolder = statusHolder;
        this.config = config;
        this.tlsRegistry = tlsRegistry;
    }

    /**
     * Initializes the NATS connection on application startup using configuration from application.properties.
     * Fails fast if unable to connect to the broker - application cannot function without NATS.
     * Registers the ConnectionListener to track connection state changes.
     * Configures TLS if available in the Quarkus TLS registry.
     *
     * @param startupEvent the startup event
     * @throws RuntimeException if connection to NATS broker fails
     */
    void onStartup(@Observes StartupEvent startupEvent) {
        // Validate configuration first
        config.validate();

        try {
            Options.Builder builder = new Options.Builder()
                    .servers(config.servers().toArray(String[]::new))
                    .executor(executorService)
                    .connectionListener(new NatsConnectionListener(statusHolder));

            // Add credentials if provided
            if (config.username().isPresent() && config.password().isPresent()) {
                builder.userInfo(config.username().get(), config.password().get());
            }

            // Always set the SSLContext if a TLS configuration is available in Quarkus.
            // The jnats client will only use it if the server URL has a TLS scheme (tls://, wss://).
            var tlsConfiguration = config.tlsConfigurationName()
                    .flatMap(tlsRegistry::get)
                    .or(tlsRegistry::getDefault);

            tlsConfiguration.ifPresent(cfg -> {
                try {
                    builder.sslContext(cfg.createSSLContext());
                    LOGGER.infof("Configured TLS for NATS connection using: %s",
                            config.tlsConfigurationName().orElse("default"));
                } catch (Exception e) {
                    throw new NatsConfigurationException(
                            "Failed to create SSLContext from TLS configuration: " +
                                    config.tlsConfigurationName().orElse("default"), e);
                }
            });

            this.connection = Nats.connect(builder.build());
            this.jetStream = connection.jetStream();
            statusHolder.setStatus(ConnectionStatus.CONNECTED);

            String servers = String.join(", ", sanitizeUrls(config.servers()));
            LOGGER.infof("Successfully connected to NATS broker(s): %s", servers);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            statusHolder.setStatus(ConnectionStatus.DISCONNECTED);

            // Fail fast - application cannot function without NATS
            String servers = String.join(", ", sanitizeUrls(config.servers()));
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
     * Gracefully closes the NATS connection on application shutdown.
     *
     * @param shutdownEvent the shutdown event
     */
    void onShutdown(@Observes ShutdownEvent shutdownEvent) {
        if (connection != null && !connection.getStatus().equals(Connection.Status.CLOSED)) {
            try {
                connection.close();
                statusHolder.setStatus(ConnectionStatus.CLOSED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns the shared JetStream connection.
     * Do not close this connection; lifecycle is managed by Quarkus.
     *
     * @return the JetStream connection
     * @throws IllegalStateException if JetStream is not available (connection failed at startup)
     */
    public JetStream getJetStream() {
        if (jetStream == null) {
            throw new IllegalStateException(
                    "JetStream is not available. NATS connection failed at startup. " +
                            "Check that NATS server is running and configuration is correct.");
        }
        return jetStream;
    }

    /**
     * Returns the shared NATS connection.
     * Do not close this connection; lifecycle is managed by Quarkus.
     *
     * @return the NATS connection
     * @throws IllegalStateException if connection is not available (connection failed at startup)
     */
    public Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException(
                    "NATS connection is not available. Connection failed at startup. " +
                            "Check that NATS server is running and configuration is correct.");
        }
        return connection;
    }
}
