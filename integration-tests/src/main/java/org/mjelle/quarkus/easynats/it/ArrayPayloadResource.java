package org.mjelle.quarkus.easynats.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.it.model.MyArrayItemEvent;

@Path("/array-payload")
public class ArrayPayloadResource {

    @Inject
    NatsPublisher<MyArrayItemEvent[]> publisher;

    @Inject
    ArrayPayloadSubscriber subscriber;

    @POST
    @Path("/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    public void publish(MyArrayItemEvent[] event) {
        try {
            publisher.publish("test.array", event);
        } catch (org.mjelle.quarkus.easynats.PublishingException e) {
            throw new RuntimeException(e);
        }
    }

    @GET
    @Path("/get-last-message")
    @Produces(MediaType.APPLICATION_JSON)
    public MyArrayItemEvent[] getLastMessage() {
        return subscriber.getLastMessage();
    }
}
