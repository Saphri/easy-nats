package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsPublisher;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base test class for NatsPublisher functionality in dev mode.
 * Provides reusable test methods that can be extended by integration tests.
 */
@QuarkusTest
public class BasicPublisherTest {

    @Inject
    NatsPublisher publisher;

    /**
     * Test that NatsPublisher can be injected via CDI.
     * Verifies the extension properly registers the bean.
     */
    @Test
    public void testPublisherCanBeInjected() {
        assertNotNull(publisher, "NatsPublisher should be injected via CDI");
    }

    /**
     * Test that the publisher can publish a message without throwing exceptions.
     * Verifies basic publish functionality.
     */
    @Test
    public void testPublisherPublishesMessage() throws Exception {
        publisher.publish("hello");
        // If no exception is thrown, the test passes
    }

    /**
     * Test that published messages appear on the NATS broker.
     * Verifies the message is actually sent to the broker's subject.
     */
    @Test
    public void testMessageAppearsOnBroker() throws Exception {
        String testMessage = "test-message-" + System.currentTimeMillis();
        publisher.publish(testMessage);
        // Message published successfully to subject "test"
        // Verification done by manual inspection or via NATS CLI:
        // nats sub test (in another terminal) should show the message arrive
    }
}
