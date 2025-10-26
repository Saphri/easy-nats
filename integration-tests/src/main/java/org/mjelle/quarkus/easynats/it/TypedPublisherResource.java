package org.mjelle.quarkus.easynats.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;

/**
 * REST endpoints for typed message publishing with CloudEvents support.
 * Demonstrates type-safe publishing using path-based endpoint separation.
 *
 * Each endpoint creates a properly typed NatsPublisher<T> at runtime to ensure
 * type safety while working with Jakarta REST's payload deserialization.
 */
@Path("/typed-publisher")
@Produces(MediaType.APPLICATION_JSON)
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
     * Demonstrates type-safe publishing with String generic type.
     *
     * @param message the message string to publish
     * @return the publish result
     */
    @POST
    @Path("/string")
    public PublishResponse publishString(String message) {
        try {
            if (message == null) {
                return new PublishResponse("error", "Message cannot be null");
            }

            NatsPublisher<String> stringPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            stringPublisher.publish(message);
            return new PublishResponse("published", "Message published successfully");
        } catch (IllegalArgumentException e) {
            return new PublishResponse("error", e.getMessage());
        } catch (Exception e) {
            return new PublishResponse("error", "Failed to publish: " + e.getMessage());
        }
    }

    /**
     * Publish a TestOrder payload to NATS.
     * Demonstrates type-safe publishing with POJO generic type.
     *
     * @param order the order object to publish
     * @return the publish result
     */
    @POST
    @Path("/order")
    public PublishResponse publishOrder(TestOrder order) {
        try {
            if (order == null) {
                return new PublishResponse("error", "Order cannot be null");
            }

            NatsPublisher<TestOrder> orderPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            orderPublisher.publish(order);
            return new PublishResponse("published", "Order published successfully");
        } catch (IllegalArgumentException e) {
            return new PublishResponse("error", e.getMessage());
        } catch (Exception e) {
            return new PublishResponse("error", "Failed to publish: " + e.getMessage());
        }
    }

    /**
     * Publish a String payload with CloudEvents metadata headers to NATS.
     *
     * @param message the message string to publish
     * @return the CloudEvents publish result with generated metadata
     */
    @POST
    @Path("/string-cloudevents")
    public CloudEventsResponse publishStringCloudEvents(String message) {
        try {
            if (message == null) {
                return new CloudEventsResponse("error", "Message cannot be null", null, null, null, null);
            }

            NatsPublisher<String> stringPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            stringPublisher.publishCloudEvent(message, null, null);

            // Generate metadata for response
            String ceId = java.util.UUID.randomUUID().toString();
            String ceTime = java.time.Instant.now().toString();
            String ceSource = getDefaultSource();

            return new CloudEventsResponse(
                "published",
                "Message published successfully",
                String.class.getCanonicalName(),
                ceSource,
                ceId,
                ceTime
            );
        } catch (IllegalArgumentException e) {
            return new CloudEventsResponse("error", e.getMessage(), null, null, null, null);
        } catch (Exception e) {
            return new CloudEventsResponse("error", "Failed to publish: " + e.getMessage(), null, null, null, null);
        }
    }

    /**
     * Publish a TestOrder payload with CloudEvents metadata headers to NATS.
     *
     * @param order the order object to publish
     * @return the CloudEvents publish result with generated metadata
     */
    @POST
    @Path("/order-cloudevents")
    public CloudEventsResponse publishOrderCloudEvents(TestOrder order) {
        try {
            if (order == null) {
                return new CloudEventsResponse("error", "Order cannot be null", null, null, null, null);
            }

            NatsPublisher<TestOrder> orderPublisher = new NatsPublisher<>(
                connectionManager,
                objectMapper
            );
            orderPublisher.publishCloudEvent(order, null, null);

            // Generate metadata for response
            String ceId = java.util.UUID.randomUUID().toString();
            String ceTime = java.time.Instant.now().toString();
            String ceSource = getDefaultSource();

            return new CloudEventsResponse(
                "published",
                "Order published successfully",
                TestOrder.class.getCanonicalName(),
                ceSource,
                ceId,
                ceTime
            );
        } catch (IllegalArgumentException e) {
            return new CloudEventsResponse("error", e.getMessage(), null, null, null, null);
        } catch (Exception e) {
            return new CloudEventsResponse("error", "Failed to publish: " + e.getMessage(), null, null, null, null);
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
     * Response for simple publishing.
     */
    public static class PublishResponse {
        public String status;
        public String message;

        public PublishResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    /**
     * Response for CloudEvents publishing.
     */
    public static class CloudEventsResponse {
        public String status;
        public String message;
        public String ceType;
        public String ceSource;
        public String ceId;
        public String ceTime;

        public CloudEventsResponse(
            String status,
            String message,
            String ceType,
            String ceSource,
            String ceId,
            String ceTime
        ) {
            this.status = status;
            this.message = message;
            this.ceType = ceType;
            this.ceSource = ceSource;
            this.ceId = ceId;
            this.ceTime = ceTime;
        }
    }
}
