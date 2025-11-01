package org.mjelle.quarkus.easynats.it;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConnectionManager;

/**
 * Initializes NATS JetStream streams on application startup.
 *
 * This bean ensures that required test streams are created when using Dev Services.
 * It only initializes streams if they don't already exist.
 */
@ApplicationScoped
public class StreamInitializer {

    private static final Logger logger = Logger.getLogger(StreamInitializer.class);
    private static final String STREAM_NAME = "test-stream";
    private static final String DURABLE_CONSUMER_NAME = "test-consumer";

    private final NatsConnectionManager connectionManager;

    StreamInitializer(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    void initializeStreamsOnStartup(@Observes @Priority(10) StartupEvent event) {
        try {
            logger.info("Initializing NATS streams for integration tests (priority 10 - runs early)");
            Connection connection = connectionManager.getConnection();
            createStreamIfNotExists(connection);
            createDurableConsumerIfNotExists(connection);
            logger.info("NATS streams initialized successfully");
        } catch (Exception e) {
            // Log but don't fail startup - individual tests will handle errors
            logger.error("Failed to initialize NATS streams on startup", e);
        }
    }

    private void createStreamIfNotExists(Connection nc) throws Exception {
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

    private void createDurableConsumerIfNotExists(Connection nc) throws Exception {
        JetStreamManagement jsm = nc.jetStreamManagement();

        // Create durable consumer
        ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                .durable(DURABLE_CONSUMER_NAME)
                .filterSubject("test.>")
                .build();
        jsm.addOrUpdateConsumer(STREAM_NAME, consumerConfig);
    }
}
