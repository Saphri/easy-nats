package org.mjelle.quarkus.easynats.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST resource for testing NatsConnection access.
 * <p>
 * Provides endpoints to:
 * 1. Get connection info (server URL, status)
 * 2. Publish messages directly via the connection
 * 3. Check connection status
 * </p>
 */
@ApplicationScoped
@Path("/connection")
public class ConnectionTestResource {

    private static final Logger LOGGER = Logger.getLogger(ConnectionTestResource.class);

    private final NatsConnection connection;

    /**
     * Constructor injection of NatsConnection.
     *
     * @param connection the injected NATS connection
     */
    public ConnectionTestResource(NatsConnection connection) {
        this.connection = connection;
    }

    /**
     * Returns information about the NATS connection.
     *
     * @return a map containing connection information
     */
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getConnectionInfo() {
        LOGGER.info("Getting connection info");

        return Map.of(
                "connectedUrl", connection.getConnectedUrl(),
                "status", connection.getStatus().toString(),
                "closed", connection.isClosed(),
                "serverVersion", connection.getServerInfo().getVersion()
        );
    }

    /**
     * Publishes a message to the specified subject.
     *
     * @param subject the subject to publish to
     * @param message the message content
     * @return a response indicating success
     */
    @POST
    @Path("/publish/{subject}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> publishMessage(
            @PathParam("subject") String subject,
            String message
    ) {
        try {
            LOGGER.infof("Publishing message to subject: %s", subject);
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            connection.publish(subject, payload);

            return Map.of(
                    "status", "success",
                    "subject", subject,
                    "message", message
            );
        } catch (IOException e) {
            LOGGER.errorf(e, "Failed to publish message to subject: %s", subject);
            throw new WebApplicationException("Failed to publish message: " + e.getMessage(), 500);
        }
    }

    /**
     * Checks if the connection is active (not closed).
     *
     * @return a map containing the connection status
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
                "status", connection.getStatus().toString(),
                "active", !connection.isClosed()
        );
    }

    /**
     * Tests that close() is a no-op and does not close the underlying connection.
     *
     * @return a map indicating the test result
     */
    @POST
    @Path("/test-close-noop")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> testCloseNoOp() {
        LOGGER.info("Testing close() no-op behavior");

        boolean wasClosedBefore = connection.isClosed();

        // Call close() - should be a no-op
        connection.close();

        boolean isClosedAfter = connection.isClosed();

        return Map.of(
                "wasClosedBefore", wasClosedBefore,
                "isClosedAfter", isClosedAfter,
                "closeIsNoOp", !wasClosedBefore && !isClosedAfter
        );
    }
}
