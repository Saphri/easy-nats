package org.mjelle.quarkus.easynats.it.example;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

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
 *      -d '{"name": "World"}'
 * </pre>
 */
@Path("/example/greeting")
public class GreetingResource {

    @Inject
    @NatsSubject("test.example.greetings")
    NatsPublisher<String> publisher;

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
    @Produces(MediaType.APPLICATION_JSON)
    public GreetingResponse sendGreeting(GreetingRequest request) {
        try {
            String message = String.format("Hello, %s!", request.name);
            publisher.publish(message);

            return new GreetingResponse(
                "success",
                String.format("Greeting sent: %s", message)
            );
        } catch (Exception e) {
            return new GreetingResponse(
                "error",
                String.format("Failed to send greeting: %s", e.getMessage())
            );
        }
    }

    /**
     * Request body for greeting endpoint.
     */
    public record GreetingRequest(String name) {}

    /**
     * Response body for greeting endpoint.
     */
    public record GreetingResponse(String status, String message) {}
}
