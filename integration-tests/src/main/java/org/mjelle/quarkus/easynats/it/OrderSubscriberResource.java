package org.mjelle.quarkus.easynats.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.mjelle.quarkus.easynats.it.model.OrderData;

import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * REST resource for TypedSubscriber integration tests.
 *
 * <p>
 * Provides endpoints to:
 * 1. Publish CloudEvent messages to test subjects
 * 2. Retrieve last received typed objects for verification
 * </p>
 */
@ApplicationScoped
@Path("/subscribe")
@RunOnVirtualThread
public class OrderSubscriberResource {

    private OrderListener orderListener;

    public OrderSubscriberResource(OrderListener orderListener) {
        this.orderListener = orderListener;
    }

    @GET
    @Path("/last-order")
    @Produces(MediaType.APPLICATION_JSON)
    public OrderData getLastOrderPojo() {
        OrderData order = orderListener.getLastOrderPojoRef().get();
        if (order == null) {
            throw new NotFoundException();
        }
        return order;
    }
}
