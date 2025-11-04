package org.mjelle.quarkus.easynats.it;

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

import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * REST endpoints for testing explicit acknowledgment mode.
 *
 * <p>Provides endpoints to: 1. Publish test orders to NATS 2. Retrieve the last order that was
 * explicitly acknowledged 3. Check for errors during processing
 */
@Path("/ack")
@RunOnVirtualThread
public class AckOrderResource {

  private final AckOrderListener ackOrderListener;
  private final NatsPublisher<OrderData> orderPublisher;

  AckOrderResource(
      AckOrderListener ackOrderListener,
      @NatsSubject("test.ack.order") NatsPublisher<OrderData> orderPublisher) {
    this.ackOrderListener = ackOrderListener;
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
  @Path("/last-acked")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getLastAckedOrder() {
    OrderData last = ackOrderListener.getLastAckedOrder();
    if (last == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(last).build();
  }

  @GET
  @Path("/error")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAckError() {
    String error = ackOrderListener.getAckError();
    if (error == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(java.util.Collections.singletonMap("error", error)).build();
  }

  @POST
  @Path("/reset")
  public Response reset() {
    ackOrderListener.reset();
    return Response.ok().build();
  }
}
