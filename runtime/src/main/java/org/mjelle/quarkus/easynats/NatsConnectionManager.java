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

/**
 * Manages the singleton NATS JetStream connection for the entire application.
 *
 * Initialization: Connects to NATS broker on application startup.
 * Shutdown: Gracefully closes the connection on application shutdown.
 */
@ApplicationScoped
public class NatsConnectionManager {

    private Connection connection;
    private JetStream jetStream;

    /**
     * Initializes the NATS connection on application startup.
     * Fails fast if unable to connect to the broker.
     *
     * @param startupEvent the startup event
     * @throws RuntimeException if connection fails
     */
    void onStartup(@Observes StartupEvent startupEvent) {
        try {
            Options options = new Options.Builder()
                    .server("nats://localhost:4222")
                    .userInfo("guest", "guest")
                    .build();

            this.connection = Nats.connect(options);
            this.jetStream = connection.jetStream();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to connect to NATS broker at nats://localhost:4222", e);
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
}
