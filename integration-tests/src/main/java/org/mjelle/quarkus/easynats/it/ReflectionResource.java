package org.mjelle.quarkus.easynats.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.PublishingException;

@Path("/reflection")
@ApplicationScoped
public class ReflectionResource {

  private static final List<ReflectionTestMessage> receivedMessages = new CopyOnWriteArrayList<>();

  @Inject NatsPublisher<ReflectionTestMessage> publisher;

  @NatsSubscriber(subject = "test.reflection-test")
  public void onMessage(ReflectionTestMessage message) {
    receivedMessages.add(message);
  }

  @GET
  @Path("/publish")
  @Produces(MediaType.TEXT_PLAIN)
  public String publishMessage(@QueryParam("message") String messageContent)
      throws PublishingException {
    publisher.publish("test.reflection-test", new ReflectionTestMessage(messageContent));
    return "Message published";
  }

  @GET
  @Path("/messages")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReflectionTestMessage> getMessages() {
    return new ArrayList<>(receivedMessages);
  }

  @GET
  @Path("/clear")
  public String clearMessages() {
    receivedMessages.clear();
    return "Messages cleared";
  }

  public static class ReflectionTestMessage {
    private String message;

    public ReflectionTestMessage() {}

    public ReflectionTestMessage(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }
  }
}
