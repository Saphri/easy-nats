package org.mjelle.quarkus.easynats.it;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.PublishingException;
import org.mjelle.quarkus.easynats.it.model.OrderData;

import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * REST endpoints for typed message publishing with CloudEvents support.
 * Demonstrates type-safe publishing using path-based endpoint separation.
 * Returns proper HTTP status codes: 204 No Content for success, 400 for bad request, 500 for errors.
 */
@Path("/publish")
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class OrderPublisherResource {

    private final NatsPublisher<OrderData> orderPublisher;

    /**
     * Constructor injection - typed publishers provided by Quarkus CDI via NatsPublisherFactory.
     */
    OrderPublisherResource(@NatsSubject("test.plain.order") NatsPublisher<OrderData> orderPublisher) {
        this.orderPublisher = orderPublisher;
    }

    /**
     * Publish a TestOrder payload to NATS.
     * Returns 204 No Content on success, 400 on null, 500 on error.
     *
     * @param order the order object to publish
     * @return 204 No Content if successful, 400 if order is null, 500 if error
     */
    @POST
    @Path("/order")
    public void publishOrder(OrderData order) {
        try {
            if (order == null) {
                throw new BadRequestException("Order cannot be null");
            }

            orderPublisher.publish(order);
        } catch (PublishingException e) {
            throw new InternalServerErrorException(e);
        }
    }
}
