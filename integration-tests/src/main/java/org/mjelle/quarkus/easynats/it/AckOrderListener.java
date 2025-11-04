package org.mjelle.quarkus.easynats.it;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.it.model.OrderData;

/**
 * Subscriber bean demonstrating explicit acknowledgment mode.
 *
 * <p>Uses NatsMessage&lt;OrderData&gt; parameter to gain explicit control over message
 * acknowledgment. The framework will NOT automatically acknowledge or negatively acknowledge
 * messages—this bean must call ack() after successful processing.
 */
@ApplicationScoped
public class AckOrderListener {

  private static final Logger LOGGER = Logger.getLogger(AckOrderListener.class);

  private AtomicReference<OrderData> lastAckedOrder = new AtomicReference<>(null);
  private AtomicReference<String> ackError = new AtomicReference<>(null);

  @NatsSubscriber(subject = "test.ack.order")
  public void handleOrderWithAck(final NatsMessage<OrderData> msg) {
    try {
      OrderData order = msg.payload();
      LOGGER.infof("📦 [EXPLICIT ACK] Received order: %s (subject=%s)", order, msg.subject());

      // Process the order
      lastAckedOrder.set(order);

      // Explicitly acknowledge after successful processing
      msg.ack();
      LOGGER.infof("✅ Order acked: %s", order.orderId());
    } catch (Exception e) {
      ackError.set(e.getMessage());
      LOGGER.errorf(e, "❌ Failed to process order");
      throw new RuntimeException(e);
    }
  }

  public OrderData getLastAckedOrder() {
    return lastAckedOrder.get();
  }

  public String getAckError() {
    return ackError.get();
  }

  public void reset() {
    lastAckedOrder.set(null);
    ackError.set(null);
  }
}
