package org.mjelle.quarkus.easynats;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for subscribing to NATS JetStream messages.
 *
 * <p>
 * Methods annotated with {@code @NatsSubscriber} will automatically subscribe to the specified
 * NATS subject and receive messages. The annotated method must have a single parameter of type
 * {@link String}, which will receive the message payload.
 * </p>
 *
 * <p>
 * Message acknowledgment is handled implicitly:
 * </p>
 * <ul>
 * <li>If the method completes successfully, the message is acknowledged (ack)</li>
 * <li>If the method throws an exception, the message is negatively acknowledged (nak)</li>
 * </ul>
 *
 * <p>
 * Example:
 * </p>
 *
 * <pre>
 * {@code
 * @ApplicationScoped
 * public class MyNatsConsumer {
 *     @NatsSubscriber("my-subject")
 *     public void onMessage(String message) {
 *         System.out.println("Received: " + message);
 *     }
 * }
 * }
 * </pre>
 *
 * @see NatsConnectionManager
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NatsSubscriber {
    /**
     * The NATS subject to subscribe to.
     *
     * @return the subject name (must not be empty)
     */
    String value();
}
