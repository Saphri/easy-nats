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
 * The annotation supports two modes:
 * </p>
 * <ul>
 * <li><strong>Ephemeral Mode</strong>: Use the {@code value()} property to subscribe to a subject.
 * The consumer is created dynamically and does not survive restarts.</li>
 * <li><strong>Durable Mode</strong>: Use {@code stream()} and {@code consumer()} properties to bind
 * to a pre-configured durable consumer. The consumer must exist on the NATS server and will preserve
 * messages across application restarts.</li>
 * </ul>
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
 * Example (Ephemeral Mode):
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
 * <p>
 * Example (Durable Mode):
 * </p>
 *
 * <pre>
 * {@code
 * @ApplicationScoped
 * public class MyDurableConsumer {
 *     @NatsSubscriber(stream = "orders", consumer = "processor")
 *     public void onOrder(String orderData) {
 *         System.out.println("Processing order: " + orderData);
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
     * The NATS subject to subscribe to (ephemeral mode).
     *
     * <p>
     * Use this property for ephemeral consumers. Cannot be combined with {@code stream()} or
     * {@code consumer()}.
     * </p>
     *
     * @return the subject name (must be non-empty for ephemeral mode)
     */
    String value() default "";

    /**
     * The NATS JetStream stream name (durable mode).
     *
     * <p>
     * Use this property with {@code consumer()} for durable consumers. The stream must exist on the
     * NATS server. Cannot be combined with {@code value()}.
     * </p>
     *
     * @return the stream name (must be non-empty and paired with consumer())
     */
    String stream() default "";

    /**
     * The NATS JetStream durable consumer name (durable mode).
     *
     * <p>
     * Use this property with {@code stream()} for durable consumers. The consumer must be
     * pre-configured on the NATS server. Cannot be combined with {@code value()}.
     * </p>
     *
     * @return the consumer name (must be non-empty and paired with stream())
     */
    String consumer() default "";
}
