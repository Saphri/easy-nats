package org.mjelle.quarkus.easynats.runtime.handler;

import io.nats.client.Message;

/**
 * Handles message delivery for a subscriber.
 *
 * <p>
 * Implementations of this interface handle the invocation of subscriber methods and manage
 * message acknowledgment based on the outcome of the subscriber method execution.
 * </p>
 */
public interface MessageHandler {

    /**
     * Handles a message received from NATS.
     *
     * <p>
     * Implementations should:
     * </p>
     * <ul>
     * <li>Extract the payload from the message</li>
     * <li>Invoke the subscriber method with the payload</li>
     * <li>Acknowledge the message if successful (ack)</li>
     * <li>Negatively acknowledge the message if an exception occurs (nak)</li>
     * <li>Log any errors at ERROR level without including the payload</li>
     * </ul>
     *
     * @param message the NATS message to handle
     */
    void handle(Message message);
}
