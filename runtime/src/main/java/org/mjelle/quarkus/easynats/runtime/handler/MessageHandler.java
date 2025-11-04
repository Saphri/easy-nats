package org.mjelle.quarkus.easynats.runtime.handler;

import io.nats.client.Message;

/**
 * Handles message delivery for a subscriber.
 *
 * <p>Implementations of this interface handle the invocation of subscriber methods and manage
 * message acknowledgment based on the outcome of the subscriber method execution.
 */
public interface MessageHandler {

  /**
   * Handles a message received from NATS.
   *
   * <p>Implementations should:
   *
   * <ul>
   *   <li>Extract the payload from the message
   *   <li>Invoke the subscriber method with the payload
   *   <li>Acknowledge the message if successful (ack)
   *   <li>Negatively acknowledge the message if an exception occurs (nak)
   *   <li>Log any errors at ERROR level without including the payload
   * </ul>
   *
   * @param message the NATS message to handle
   */
  void handle(Message message);
}
