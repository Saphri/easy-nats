package org.mjelle.quarkus.easynats.it;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.PublishingException;
import org.mjelle.quarkus.easynats.it.model.OrderData;

/**
 * REST endpoints for testing negative acknowledgment (nak) mode.
 *
 * <p>
 * Provides endpoints to:
 * 1. Publish test orders to NATS
 * 2. Retrieve the last order that was processed (after redelivery)
 * 3. Check redelivery count
 * 4. Check for errors during processing
 * </p>
 */
@Path("/nak")
@RunOnVirtualThread
public class NakOrderResource {

    private final NakOrderListener nakOrderListener;
    private final NatsPublisher<OrderData> orderPublisher;

    NakOrderResource(
            NakOrderListener nakOrderListener,
            @NatsSubject("test.order") NatsPublisher<OrderData> orderPublisher) {
        this.nakOrderListener = nakOrderListener;
        this.orderPublisher = orderPublisher;
    }

    @POST
    @Path("/publish")
    @Produces(MediaType.APPLICATION_JSON)
    public Response publishOrder(OrderData order) {
        try {
            if (order == null) {
                throw new BadRequestException("Order cannot be null");
            }
            orderPublisher.publish(order);
            return Response.accepted().build();
        } catch (PublishingException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @GET
    @Path("/last-processed")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLastProcessedOrder() {
        OrderData last = nakOrderListener.getLastProcessedOrder();
        if (last == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(last).build();
    }

    @GET
    @Path("/redelivery-count")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLastRedeliveryCount() {
        int count = nakOrderListener.getLastRedeliveryCount();
        return Response.ok(java.util.Collections.singletonMap("redeliveryCount", count)).build();
    }

    @GET
    @Path("/error")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNakError() {
        String error = nakOrderListener.getLastNakError();
        if (error == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(java.util.Collections.singletonMap("error", error)).build();
    }

    @POST
    @Path("/reset")
    public Response reset() {
        nakOrderListener.reset();
        return Response.ok().build();
    }
}
