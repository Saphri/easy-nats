package org.mjelle.quarkus.easynats;

import io.nats.client.JetStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Simple injectable wrapper for publishing string messages to NATS JetStream.
 *
 * All messages are published to the hardcoded subject "test" with raw UTF-8 bytes.
 */
@ApplicationScoped
public class NatsPublisher {

    @Inject
    NatsConnectionManager connectionManager;

    /**
     * Publishes a string message to the hardcoded NATS subject "test".
     *
     * @param message the raw message content to publish (must not be null)
     * @throws Exception if publication fails (connection error, broker unreachable, etc.)
     */
    public void publish(String message) throws Exception {
        if (message == null) {
            throw new NullPointerException("Message must not be null");
        }

        JetStream jetStream = connectionManager.getJetStream();
        jetStream.publish("test", message.getBytes());
    }
}
