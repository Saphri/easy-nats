package org.mjelle.quarkus.easynats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import io.quarkus.virtual.threads.VirtualThreads;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatus;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;
import org.mjelle.quarkus.easynats.runtime.health.NatsConnectionListener;

/**
 * Manages the singleton NATS JetStream connection for the entire application.
 *
 * Initialization: Connects to NATS broker on application startup (or logs warning if unavailable).
 * Shutdown: Gracefully closes the connection on application shutdown.
 */
@ApplicationScoped // Note: @ApplicationScoped provides singleton semantics in Quarkus
public class NatsConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(NatsConnectionManager.class.getName());

    private final ExecutorService executorService;
    private final ConnectionStatusHolder statusHolder;

    private Connection connection;
    private JetStream jetStream;

    public NatsConnectionManager(@VirtualThreads ExecutorService executorService, ConnectionStatusHolder statusHolder) {
        this.executorService = executorService;
        this.statusHolder = statusHolder;
    }

    /**
     * Initializes the NATS connection on application startup.
     * Logs a warning if unable to connect to the broker instead of failing startup.
     * Registers the ConnectionListener to track connection state changes.
     *
     * @param startupEvent the startup event
     */
    void onStartup(@Observes StartupEvent startupEvent) {
        try {
            Options options = new Options.Builder()
                    .server("nats://localhost:4222")
                    .userInfo("guest", "guest")
                    .executor(executorService)
                    .connectionListener(new NatsConnectionListener(statusHolder))
                    .build();

            this.connection = Nats.connect(options);
            this.jetStream = connection.jetStream();
            statusHolder.setStatus(ConnectionStatus.CONNECTED);
            LOGGER.info("Connected to NATS broker at nats://localhost:4222");
        } catch (IOException e) {
            statusHolder.setStatus(ConnectionStatus.DISCONNECTED);
            LOGGER.warning("Failed to connect to NATS broker at nats://localhost:4222. " +
                    "NatsPublisher will not be functional until broker is available: " + e.getMessage());
        } catch (InterruptedException e) {
            statusHolder.setStatus(ConnectionStatus.DISCONNECTED);
            Thread.currentThread().interrupt();
            LOGGER.warning("NATS connection interrupted during startup: " + e.getMessage());
        }
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
     */
    public JetStream getJetStream() {
        return jetStream;
    }

    /**
     * Returns the shared NATS connection.
     * Do not close this connection; lifecycle is managed by Quarkus.
     *
     * @return the NATS connection
     */
    public Connection getConnection() {
        return connection;
    }
}
