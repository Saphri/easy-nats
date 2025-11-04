package org.mjelle.quarkus.easynats.it;

import java.util.Map;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.PublishingException;
import org.mjelle.quarkus.easynats.it.model.OrderData;

import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * REST endpoints for testing negative acknowledgment (nak) mode.
 *
 * <p>Provides endpoints to: 1. Publish test orders to NATS 2. Retrieve the last order that was
 * processed (after redelivery) 3. Check redelivery count 4. Check for errors during processing
 */
@Path("/nak")
@RunOnVirtualThread
public class NakOrderResource {

  private final NakOrderListener nakOrderListener;
  private final NatsPublisher<OrderData> orderPublisher;

  NakOrderResource(
      NakOrderListener nakOrderListener,
      @NatsSubject("test.nak.order") NatsPublisher<OrderData> orderPublisher) {
    this.nakOrderListener = nakOrderListener;
    this.orderPublisher = orderPublisher;
  }

  @POST
  @Path("/publish")
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

  @GET
  @Path("/last-processed")
  @Produces(MediaType.APPLICATION_JSON)
  public OrderData getLastProcessedOrder() {
    OrderData last = nakOrderListener.getLastProcessedOrder();
    if (last == null) {
      throw new NotFoundException("No processed order found");
    }
    return last;
  }

  @GET
  @Path("/redelivery-count")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Integer> getLastRedeliveryCount() {
    int count = nakOrderListener.getLastRedeliveryCount();
    return Map.of("redeliveryCount", count);
  }

  @GET
  @Path("/error")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, String> getNakError() {
    String error = nakOrderListener.getLastNakError();
    if (error == null) {
      throw new NotFoundException("No error found");
    }
    return Map.of("error", error);
  }

  @POST
  @Path("/reset")
  public void reset() {
    nakOrderListener.reset();
  }
}
