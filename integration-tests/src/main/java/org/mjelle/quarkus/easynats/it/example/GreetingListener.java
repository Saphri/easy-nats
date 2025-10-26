package org.mjelle.quarkus.easynats.it.example;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsSubscriber;

/**
 * Example listener that demonstrates the @NatsSubscriber annotation.
 *
 * This listener automatically subscribes to the "test.example.greetings" subject
 * when the application starts and prints any received messages to the console.
 */
@ApplicationScoped
public class GreetingListener {

    private static final Logger LOGGER = Logger.getLogger(GreetingListener.class);

    /**
     * Handles greeting messages received on the "test.example.greetings" subject.
     *
     * The method will be invoked for each message received, and the message
     * will be automatically acknowledged (ack) on success or negatively
     * acknowledged (nak) if an exception is thrown.
     *
     * @param message the greeting message received from NATS
     */
    @NatsSubscriber("test.example.greetings")
    public void onGreeting(String message) {
        LOGGER.infof("📩 Received greeting: %s", message);

        // You can add your business logic here
        // The message is automatically ack'd if this method completes successfully
        // or nak'd if an exception is thrown
    }
}
