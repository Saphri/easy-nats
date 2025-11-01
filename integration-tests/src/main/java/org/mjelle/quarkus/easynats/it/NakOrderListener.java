package org.mjelle.quarkus.easynats.it;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsMessage;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.it.model.OrderData;

/**
 * Subscriber bean demonstrating negative acknowledgment (nak) mode.
 *
 * <p>
 * Uses NatsMessage&lt;OrderData&gt; parameter to gain explicit control over message acknowledgment.
 * This bean demonstrates handling transient errors by calling nak() to request redelivery.
 * </p>
 */
@ApplicationScoped
public class NakOrderListener {

    private static final Logger LOGGER = Logger.getLogger(NakOrderListener.class);

    private AtomicInteger redeliveryCount = new AtomicInteger(0);
    private AtomicReference<OrderData> lastProcessedOrder = new AtomicReference<>(null);
    private AtomicReference<String> lastNakError = new AtomicReference<>(null);

    @NatsSubscriber(subject = "test.order")
    public void handleOrderWithNak(final NatsMessage<OrderData> msg) {
        try {
            OrderData order = msg.payload();
            // deliveredCount() = 1 on first delivery, 2 on first redelivery, etc.
            long deliveredCount = msg.metadata().deliveredCount();

            LOGGER.infof(
                    "📦 [NAK] Received order: %s (deliveredCount=%d, subject=%s)",
                    order, deliveredCount, msg.subject());

            redeliveryCount.set((int) (deliveredCount - 1)); // Convert to redelivery count (0-based)
            lastProcessedOrder.set(order);

            // Simulate a transient error: reject first attempt, accept on second try
            if (deliveredCount < 2) {
                LOGGER.infof("❌ Transient error on order %s, requesting redelivery", order.orderId());
                msg.nak(); // Request immediate redelivery
            } else {
                // On redelivery, acknowledge successful processing
                LOGGER.infof("✅ Order processed successfully on retry: %s", order.orderId());
                msg.ack();
            }
        } catch (Exception e) {
            lastNakError.set(e.getMessage());
            LOGGER.errorf(e, "❌ Failed to process order");
            throw new RuntimeException(e);
        }
    }

    public int getLastRedeliveryCount() {
        return redeliveryCount.get();
    }

    public OrderData getLastProcessedOrder() {
        return lastProcessedOrder.get();
    }

    public String getLastNakError() {
        return lastNakError.get();
    }

    public void reset() {
        redeliveryCount.set(0);
        lastProcessedOrder.set(null);
        lastNakError.set(null);
    }
}
