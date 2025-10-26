package org.mjelle.quarkus.easynats.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
    public Response health() {
        return Response.ok("ok").build();
    }

    /**
     * Publish a message to NATS.
     * Returns 204 No Content on success, 500 on error.
     *
     * @param message the message to publish
     * @return 204 No Content if successful, 500 if error
     */
    @GET
    @Path("/message")
    public Response publishMessage(@QueryParam("message") String message) {
        try {
            if (message == null || message.trim().isEmpty()) {
                message = "test";
            }
            publisher.publish(message);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}
