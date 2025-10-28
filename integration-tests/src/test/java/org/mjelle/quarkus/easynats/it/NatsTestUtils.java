package org.mjelle.quarkus.easynats.it;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.mjelle.quarkus.easynats.NatsConnection;

import java.util.concurrent.atomic.AtomicReference;

public class NatsTestUtils {

    public static final String STREAM_NAME = "test-stream";
    public static final String DURABLE_CONSUMER_NAME = "test-consumer";
    private static final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    public static Connection getConnection() throws Exception {
        if (connectionRef.get() == null || connectionRef.get().getStatus() != Connection.Status.CONNECTED) {
            Options options = new Options.Builder()
                    .server("nats://localhost:4222")
                    .userInfo("guest", "guest")
                    .build();
            Connection nc = Nats.connect(options);
            connectionRef.set(nc);
            createStreamIfNotExists(nc);
            createDurableConsumerIfNotExists(nc);
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

    private static void createStreamIfNotExists(Connection nc) throws Exception {
        JetStreamManagement jsm = nc.jetStreamManagement();
        for (StreamInfo streamInfo : jsm.getStreams()) {
            if (streamInfo.getConfiguration().getName().equals(STREAM_NAME)) {
                return; // Stream already exists
            }
        }

        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(STREAM_NAME)
                .subjects("test.>")
                .storageType(StorageType.Memory)
                .build();
        jsm.addStream(streamConfig);
    }

    private static void createDurableConsumerIfNotExists(Connection nc) throws Exception {
        JetStreamManagement jsm = nc.jetStreamManagement();

        // Create durable consumer
        ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                .durable(DURABLE_CONSUMER_NAME)
                .filterSubject("test.>")
                .build();
        jsm.addOrUpdateConsumer(STREAM_NAME, consumerConfig);
    }

    public static void purgeStream() throws Exception {
        Connection nc = connectionRef.get();
        if (nc != null) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            jsm.purgeStream(STREAM_NAME);
        }
    }
}
