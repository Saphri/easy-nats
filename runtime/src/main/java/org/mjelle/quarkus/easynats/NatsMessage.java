package org.mjelle.quarkus.easynats;

import java.io.IOException;
import java.time.Duration;

import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;

/**
 * Type-safe wrapper providing explicit control over NATS JetStream message acknowledgment.
 *
 * <p><strong>Explicit Acknowledgment Mode:</strong> Use this interface as a subscriber method
 * parameter to gain full control over message lifecycle. The framework will NOT automatically
 * acknowledge or negatively acknowledge messages—you must call one of: {@link #ack()}, {@link
 * #nakWithDelay(Duration)}, or {@link #term()}.
 *
 * <p><strong>How to Use:</strong> Declare a parameter of type {@code NatsMessage<T>} in your
 * {@code @NatsSubscriber} method instead of the payload type directly. The framework will:
 *
 * <ul>
 *   <li>Deserialize the message payload to type T
 *   <li>Wrap it in a NatsMessage instance
 *   <li>Pass the wrapper to your method
 *   <li>Skip automatic acknowledgment (your responsibility)
 * </ul>
 *
 * <p><strong>Example - Success Path:</strong>
 *
 * <pre>
 * @NatsSubscriber(subject = "orders", stream = "ORDERS", consumer = "processor")
 * void handleOrder(NatsMessage&lt;Order&gt; msg) {
 *     Order order = msg.payload();
 *     try {
 *         processOrder(order);
 *         msg.ack();  // Message handled successfully, mark delivered
 *     } catch (TransientException e) {
 *         // Temporary failure, request redelivery after 5 seconds
 *         msg.nakWithDelay(Duration.ofSeconds(5));
 *     } catch (PermanentException e) {
 *         // Permanent failure, acknowledge to prevent infinite retries, log error
 *         logger.error("Cannot process order", e);
 *         msg.ack();
 *     }
 * }
 * </pre>
 *
 * <p><strong>Important Notes on Developer Responsibility:</strong>
 *
 * <ul>
 *   <li><strong>No Timeout Enforcement:</strong> The framework does not enforce a time limit on
 *       when you must call ack/nak/term. If you don't call any method, the NATS broker will handle
 *       the message according to the consumer's AckPolicy (may redeliver or discard).
 *   <li><strong>Async Safety:</strong> You may call ack/nak/term from async contexts (thread pools,
 *       futures), but you are responsible for ensuring the message reference remains valid. If the
 *       NATS connection is closed before you call ack/nak, the call will fail.
 *   <li><strong>No Idempotency Enforcement by Framework:</strong> Idempotency is guaranteed by the
 *       NATS JetStream broker, not by this wrapper. Calling ack() multiple times is safe because
 *       NATS handles duplicate acks transparently.
 *   <li><strong>No Rollback:</strong> If ack() succeeds and then your method throws an exception,
 *       the ack has already taken effect—there is no rollback. Plan error handling accordingly.
 * </ul>
 *
 * <p><strong>Implicit Acknowledgment Mode (Comparison):</strong> If you use the payload type
 * directly as a parameter (e.g., {@code void handleOrder(Order order)}), the framework
 * automatically:
 *
 * <ul>
 *   <li>Acks the message if the method completes without exception
 *   <li>Naks the message if the method throws any exception
 * </ul>
 *
 * Choose implicit mode for simple processing; use explicit mode for complex error handling.
 *
 * @param <T> Type of the deserialized message payload
 * @see #ack()
 * @see #nakWithDelay(Duration)
 * @see #term()
 */
public interface NatsMessage<T> {

  /**
   * Get the deserialized message payload.
   *
   * <p>The payload is deserialized at NatsMessage construction time (not lazily on method call).
   * This method returns the cached, already-deserialized instance of type T.
   *
   * <p>If payload deserialization fails during NatsMessage construction, the framework will:
   *
   * <ul>
   *   <li>NOT invoke your subscriber method
   *   <li>Log the deserialization error
   *   <li>Automatically nak the message for retry (implicit mode) or skip ack/nak (explicit mode)
   * </ul>
   *
   * @return The deserialized message payload of type T
   */
  T payload();

  /**
   * Acknowledge this message, signaling successful processing.
   *
   * <p>Once this method returns successfully, the message is marked as delivered by the NATS broker
   * and will NOT be redelivered to any consumer.
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li>Message processed successfully and result persisted
   *   <li>Permanent error: message cannot be processed, acknowledge to prevent infinite retry loop
   *   <li>Message is a duplicate of an already-processed message
   * </ul>
   *
   * <p><strong>Idempotency:</strong> Calling ack() multiple times on the same message is safe. The
   * NATS broker handles duplicate acks transparently (subsequent calls are no-ops).
   *
   * <p><strong>No Rollback:</strong> If ack() succeeds and then your method throws an exception,
   * the ack has already taken effect. The message will not be redelivered. Plan error handling
   * accordingly.
   *
   * @throws IOException if the underlying NATS connection is broken
   * @throws io.nats.client.api.JetStreamApiException if the broker rejects the acknowledgment
   */
  void ack();

  /**
   * Negative acknowledge (nak) this message immediately, requesting immediate redelivery.
   *
   * <p>Once this method returns successfully, the message is marked for redelivery with no delay.
   * The NATS broker will resend the message to the consumer immediately (subject to broker
   * backpressure).
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li>Transient failure (e.g., temporary service unavailable) that might succeed on immediate
   *       retry
   *   <li>No backoff desired; retry immediately
   * </ul>
   *
   * <p><strong>Idempotency:</strong> Calling nak() multiple times on the same message is safe. The
   * NATS broker handles duplicate naks transparently.
   *
   * <p><strong>When to Use nakWithDelay() Instead:</strong> For transient failures that benefit
   * from a delay before retry, use {@link #nakWithDelay(Duration)} to add backoff.
   *
   * @throws IOException if the underlying NATS connection is broken
   * @throws io.nats.client.api.JetStreamApiException if the broker rejects the negative
   *     acknowledgment
   * @see #nakWithDelay(Duration) for requesting redelivery with a delay
   */
  void nak();

  /**
   * Negative acknowledge (nak) this message with a specified redelivery delay.
   *
   * <p>Once this method returns successfully, the message is marked for redelivery after the
   * specified delay. The NATS broker will not immediately resend the message; it will wait for the
   * delay before offering the message back to the consumer.
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li>Transient failure (e.g., remote API rate-limited) that benefits from a delay before retry
   *   <li>Exponential backoff: compute delay based on redelivery count and nak with the calculated
   *       delay
   *   <li>Jitter/randomization: add random jitter to delay to avoid thundering herd
   * </ul>
   *
   * <p><strong>Delay Handling:</strong> The provided delay is a <strong>hint</strong> to the NATS
   * broker. The broker may honor, override, or ignore the delay based on:
   *
   * <ul>
   *   <li>Consumer configuration (AckPolicy, MaxAckPending, etc.)
   *   <li>MaxRedelivery settings (broker may give up after too many retries)
   *   <li>Backoff policy if configured on the consumer
   * </ul>
   *
   * <p><strong>Null or Zero Delay:</strong> If delay is {@code null} or {@link Duration#ZERO}, the
   * broker applies its default delay (typically immediate redelivery, equivalent to {@link
   * #nak()}).
   *
   * <p><strong>Idempotency:</strong> Calling nakWithDelay() multiple times on the same message is
   * safe. The NATS broker handles duplicate naks transparently.
   *
   * <p><strong>Example - Exponential Backoff:</strong>
   *
   * <pre>
   * int redeliveryAttempt = Math.max(0, (int) msg.metadata().deliveredCount() - 1);
   * Duration backoff = Duration.ofMillis(100L * (long) Math.pow(2, redeliveryAttempt));
   * msg.nakWithDelay(backoff);  // Exponential backoff: 100ms, 200ms, 400ms, ...
   * </pre>
   *
   * @param delay Optional redelivery delay. If {@code null} or {@link Duration#ZERO}, NATS applies
   *     the consumer's default delay (typically immediate redelivery). For positive delays, the
   *     message is reoffered after at least this duration.
   * @throws IOException if the underlying NATS connection is broken
   * @throws io.nats.client.api.JetStreamApiException if the broker rejects the negative
   *     acknowledgment
   * @see #nak() for immediate redelivery without delay
   * @see #metadata() to access redelivery count and other message metadata
   */
  void nakWithDelay(Duration delay);

  /**
   * Explicitly terminate this message without redelivery.
   *
   * <p>Marks the message as terminated, preventing redelivery even if max redelivery attempts are
   * not reached. The exact semantics depend on the NATS consumer's configuration.
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li>Message content is invalid (e.g., unparseable, corrupted)
   *   <li>Message is superseded (a newer version of the same event already exists)
   *   <li>Dead-letter queue pattern: after max retries, terminate instead of nak again
   * </ul>
   *
   * <p><strong>Note:</strong> This is a pass-through to the underlying NATS JetStream API. Some
   * NATS consumer configurations may not support termination. Consult the broker documentation and
   * test before relying on this method in production.
   *
   * @throws IOException if the underlying NATS connection is broken
   * @throws io.nats.client.api.JetStreamApiException if the broker rejects the termination
   */
  void term();

  /**
   * Get the message headers for accessing CloudEvents attributes and custom headers.
   *
   * <p>Returns all headers attached to the NATS message. Headers include:
   *
   * <ul>
   *   <li><strong>CloudEvents binary-mode attributes:</strong> Prefixed with {@code ce-} (e.g.,
   *       {@code ce-specversion}, {@code ce-type}, {@code ce-source}, {@code ce-id})
   *   <li><strong>Custom application headers:</strong> Any additional headers set by publishers
   *   <li><strong>NATS system headers:</strong> Internal NATS metadata
   * </ul>
   *
   * <p><strong>CloudEvents Example:</strong>
   *
   * <pre>
   * Headers headers = msg.headers();
   * String eventType = headers.get("ce-type").get(0);  // "com.example.order.created"
   * String eventSource = headers.get("ce-source").get(0);  // "/orders"
   * String eventId = headers.get("ce-id").get(0);
   * </pre>
   *
   * @return Headers object containing all message headers (never null)
   */
  Headers headers();

  /**
   * Get the NATS subject this message was published to.
   *
   * <p>The subject is the routing key for the message within the NATS broker.
   *
   * <p><strong>Examples:</strong>
   *
   * <ul>
   *   <li>{@code "orders"}
   *   <li>{@code "events.order.created"}
   *   <li>{@code "notifications.email.sent"}
   * </ul>
   *
   * @return The subject name (never null or empty)
   */
  String subject();

  /**
   * Get NATS JetStream message metadata (sequence numbers, redelivery count, timestamps, etc.).
   *
   * <p>Contains operational information about the message from the NATS broker:
   *
   * <ul>
   *   <li><strong>Sequence:</strong> Stream sequence number and consumer sequence number
   *   <li><strong>Redelivery Count:</strong> Number of times the message has been redelivered
   *   <li><strong>Timestamp:</strong> When the message was published
   * </ul>
   *
   * <p><strong>Useful for:</strong>
   *
   * <ul>
   *   <li>Exponential backoff: check {@code redeliveryCount()} to compute backoff duration
   *   <li>Dead-letter queues: route messages to DLQ after max redelivery attempts
   *   <li>Idempotency checks: use message sequence to detect and skip duplicates
   *   <li>Observability: track message processing timestamps and redelivery metrics
   * </ul>
   *
   * <p><strong>Example - Checking Redelivery Count:</strong>
   *
   * <pre>
   * int redeliveryCount = msg.metadata().redeliveryCount();
   * if (redeliveryCount > 3) {
   *     // Too many retries, route to dead-letter queue
   *     msg.term();
   *     logger.warn("Message exceeded max retries: sequence=" + msg.metadata().sequence());
   * }
   * </pre>
   *
   * @return NatsJetStreamMetaData containing metadata (never null)
   */
  NatsJetStreamMetaData metadata();
}
