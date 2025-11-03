package org.mjelle.quarkus.easynats.it;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.it.model.OrderData;

@ApplicationScoped
public class OrderListener {

    private static final Logger LOGGER = Logger.getLogger(OrderListener.class);

    private AtomicReference<OrderData> lastOrderPojo = new AtomicReference<>(null);

    @NatsSubscriber(subject = "test.plain.order")
    public void handleOrderPojo(final OrderData order) {
        LOGGER.infof("📦 Received order: %s", order);
        lastOrderPojo.set(order);
    }

    // Getters to allow subscriber beans to update state
    public AtomicReference<OrderData> getLastOrderPojoRef() {
        return lastOrderPojo;
    }
}
