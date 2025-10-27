package org.mjelle.quarkus.easynats.it.example;

import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.PublishingException;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * Example REST endpoint that demonstrates publishing messages to NATS.
 *
 * Send a POST request to trigger a greeting message that will be
 * received by the {@link GreetingListener}.
 *
 * Example:
 * <pre>
 * curl -X POST http://localhost:8080/example/greeting \
 *      -H "Content-Type: application/json" \
 *      -d 'World'
 * </pre>
 */
@Path("/example/greeting")
@RunOnVirtualThread
public class GreetingResource {

    private final NatsPublisher<GreetingRequest> publisher;

    public GreetingResource(@NatsSubject("test.example.greetings") NatsPublisher<GreetingRequest> publisher) {
        this.publisher = publisher;
    }

    /**
     * Sends a greeting message to NATS.
     *
     * The message will be published to the "test.example.greetings" subject
     * and received by the {@link GreetingListener}.
     *
     * @param request the greeting request containing the name
     * @return a response indicating the message was sent
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String sendGreeting(String name) {
        try {
            String message = String.format("Hello, %s!", name);
            publisher.publish(new GreetingRequest(message));

            return String.format("Greeting sent: %s", message);
        } catch (PublishingException e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Request body for greeting endpoint.
     */
    @RegisterForReflection
    public record GreetingRequest(String name) {}
}
