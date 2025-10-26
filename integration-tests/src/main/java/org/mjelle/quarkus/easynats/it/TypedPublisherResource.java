package org.mjelle.quarkus.easynats.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;

/**
 * REST endpoints for typed message publishing with CloudEvents support.
 * Demonstrates type-safe publishing using path-based endpoint separation.
 * Returns proper HTTP status codes: 204 No Content for success, 400 for bad request, 500 for errors.
 */
@Path("/typed-publisher")
@Consumes(MediaType.APPLICATION_JSON)
public class TypedPublisherResource {

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection - dependencies provided by Quarkus CDI.
     */
    TypedPublisherResource(
        NatsConnectionManager connectionManager,
        ObjectMapper objectMapper
    ) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a String payload to NATS.
     * Returns 204 No Content on success, 400 on null, 500 on error.
     *
     * @param message the message string to publish
     * @return 204 No Content if successful, 400 if message is null, 500 if error
     */
    @POST
    @Path("/string")
    public Response publishString(String message) {
        try {
            if (message == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Message cannot be null")
                    .build();
            }

            NatsPublisher<String> stringPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            stringPublisher.publish(message);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
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
    public Response publishOrder(TestOrder order) {
        try {
            if (order == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Order cannot be null")
                    .build();
            }

            NatsPublisher<TestOrder> orderPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            orderPublisher.publish(order);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Publish a String payload with CloudEvents metadata headers to NATS.
     * Returns 200 OK with CloudEvents metadata, 400 on null, 500 on error.
     *
     * @param message the message string to publish
     * @return 200 OK with CloudEvents metadata if successful, 400 if null, 500 if error
     */
    @POST
    @Path("/string-cloudevents")
    public Response publishStringCloudEvents(String message) {
        try {
            if (message == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Message cannot be null")
                    .build();
            }

            NatsPublisher<String> stringPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            stringPublisher.publishCloudEvent(message, null, null);

            // Return metadata for verification
            var ceMetadata = new CloudEventsMetadata(
                String.class.getCanonicalName(),
                getDefaultSource(),
                java.util.UUID.randomUUID().toString(),
                java.time.Instant.now().toString()
            );

            return Response.ok(ceMetadata).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Publish a TestOrder payload with CloudEvents metadata headers to NATS.
     * Returns 200 OK with CloudEvents metadata, 400 on null, 500 on error.
     *
     * @param order the order object to publish
     * @return 200 OK with CloudEvents metadata if successful, 400 if null, 500 if error
     */
    @POST
    @Path("/order-cloudevents")
    public Response publishOrderCloudEvents(TestOrder order) {
        try {
            if (order == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Order cannot be null")
                    .build();
            }

            NatsPublisher<TestOrder> orderPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            orderPublisher.publishCloudEvent(order, null, null);

            // Return metadata for verification
            var ceMetadata = new CloudEventsMetadata(
                TestOrder.class.getCanonicalName(),
                getDefaultSource(),
                java.util.UUID.randomUUID().toString(),
                java.time.Instant.now().toString()
            );

            return Response.ok(ceMetadata).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Get default source identifier (hostname or localhost).
     */
    private String getDefaultSource() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            String appName = System.getProperty("app.name");
            return appName != null ? appName : "localhost";
        }
    }

    /**
     * CloudEvents metadata returned in response body.
     */
    @RegisterForReflection
    public static class CloudEventsMetadata {
        public String ceType;
        public String ceSource;
        public String ceId;
        public String ceTime;

        public CloudEventsMetadata(String ceType, String ceSource, String ceId, String ceTime) {
            this.ceType = ceType;
            this.ceSource = ceSource;
            this.ceId = ceId;
            this.ceTime = ceTime;
        }
    }
}
