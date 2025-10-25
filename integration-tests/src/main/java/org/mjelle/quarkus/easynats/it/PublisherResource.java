package org.mjelle.quarkus.easynats.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.mjelle.quarkus.easynats.NatsPublisher;

/**
 * REST endpoint for publishing messages via NatsPublisher.
 * Used by integration tests via RestAssured.
 */
@Path("/publish")
@Produces(MediaType.APPLICATION_JSON)
public class PublisherResource {

    private final NatsPublisher publisher;

    /**
     * Constructor injection - dependencies provided by Quarkus CDI.
     */
    PublisherResource(NatsPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Health check endpoint - verifies the publisher is available.
     */
    @GET
    @Path("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "NatsPublisher is available");
    }

    /**
     * Publish a message to NATS.
     *
     * @param message the message to publish
     * @return the publish result
     */
    @GET
    @Path("/message")
    public PublishResponse publishMessage(@QueryParam("message") String message) {
        try {
            if (message == null || message.trim().isEmpty()) {
                message = "test";
            }
            publisher.publish(message);
            return new PublishResponse("success", "Message published: " + message);
        } catch (Exception e) {
            return new PublishResponse("error", "Failed to publish: " + e.getMessage());
        }
    }

    /**
     * Health response DTO.
     */
    public static class HealthResponse {
        public String status;
        public String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    /**
     * Publish response DTO.
     */
    public static class PublishResponse {
        public String status;
        public String message;

        public PublishResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}
