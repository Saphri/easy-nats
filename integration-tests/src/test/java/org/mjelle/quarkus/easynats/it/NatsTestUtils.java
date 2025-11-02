package org.mjelle.quarkus.easynats.it;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.eclipse.microprofile.config.ConfigProvider;
import org.mjelle.quarkus.easynats.NatsConnection;

import java.util.concurrent.atomic.AtomicReference;

public class NatsTestUtils {

    public static final String STREAM_NAME = "test-stream";
    public static final String DURABLE_CONSUMER_NAME = "test-consumer";
    private static final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    public static Connection getConnection() throws Exception {
        if (connectionRef.get() == null || connectionRef.get().getStatus() != Connection.Status.CONNECTED) {
            // Get connection URL from Quarkus configuration (populated by Dev Services)
            // Falls back to localhost:4222 for compatibility with explicit configuration
            String natsUrl = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.easynats.servers", String.class)
                    .orElse("nats://localhost:4222");

            String username = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.easynats.username", String.class)
                    .orElse("guest");

            String password = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.easynats.password", String.class)
                    .orElse("guest");

            Options options = new Options.Builder()
                    .server(natsUrl)
                    .userInfo(username, password)
                    .build();
            Connection nc = Nats.connect(options);
            connectionRef.set(nc);
            // Stream and consumer are now created via nats.conf
        }
        return connectionRef.get();
    }

    public static JetStream getJetStream() throws Exception {
        return getConnection().jetStream();
    }

    /**
     * Returns a NatsConnection wrapper for testing try-with-resources and lifecycle scenarios.
     * This method wraps the underlying connection in a NatsConnection facade.
     *
     * @return a NatsConnection wrapper
     * @throws Exception if unable to establish connection
     */
    public static NatsConnection getNatsConnection() throws Exception {
        return new NatsConnection(getConnection());
    }


    public static void purgeStream() throws Exception {
        Connection nc = connectionRef.get();
        if (nc != null) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            jsm.purgeStream(STREAM_NAME);
        }
    }
}
