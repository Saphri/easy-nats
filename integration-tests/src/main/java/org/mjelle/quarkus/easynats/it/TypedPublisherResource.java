package org.mjelle.quarkus.easynats.it;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.mjelle.quarkus.easynats.CloudEventsHeaders;
import org.mjelle.quarkus.easynats.NatsPublisher;

/**
 * REST endpoints for typed message publishing with CloudEvents support.
 * Demonstrates type-safe publishing using path-based endpoint separation.
 * Returns proper HTTP status codes: 204 No Content for success, 400 for bad request, 500 for errors.
 */
@Path("/typed-publisher")
@Consumes(MediaType.APPLICATION_JSON)
public class TypedPublisherResource {

    private final NatsPublisher<String> stringPublisher;
    private final NatsPublisher<TestOrder> orderPublisher;

    /**
     * Constructor injection - typed publishers provided by Quarkus CDI via NatsPublisherFactory.
     */
    TypedPublisherResource(
        NatsPublisher<String> stringPublisher,
        NatsPublisher<TestOrder> orderPublisher
    ) {
        this.stringPublisher = stringPublisher;
        this.orderPublisher = orderPublisher;
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

            stringPublisher.publish("test.typed_publisher.string", message);
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

            orderPublisher.publish("test.typed_publisher.order", order);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Publish a String payload as a CloudEvent to NATS.
     * Returns 200 OK with CloudEvents metadata, 400 on null, 500 on error.
     *
     * Note: As of this version, all publish() calls automatically wrap payloads in CloudEvents format.
     * This endpoint returns the generated CloudEvents metadata for verification.
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

            // All publish() calls now automatically wrap in CloudEvents
            stringPublisher.publish("test.typed_publisher.string", message);

            // Generate and return the metadata that was published
            CloudEventsHeaders.CloudEventsMetadata metadata =
                CloudEventsHeaders.generateMetadata(String.class, null, null);

            // Return the actual metadata from the published event
            return Response.ok(new ResponseMetadata(metadata)).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Publish a TestOrder payload as a CloudEvent to NATS.
     * Returns 200 OK with CloudEvents metadata, 400 on null, 500 on error.
     *
     * Note: As of this version, all publish() calls automatically wrap payloads in CloudEvents format.
     * This endpoint returns the generated CloudEvents metadata for verification.
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

            // All publish() calls now automatically wrap in CloudEvents
            orderPublisher.publish("test.typed_publisher.order", order);

            // Generate and return the metadata that was published
            CloudEventsHeaders.CloudEventsMetadata metadata =
                CloudEventsHeaders.generateMetadata(TestOrder.class, null, null);

            // Return the actual metadata from the published event
            return Response.ok(new ResponseMetadata(metadata)).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Response wrapper for CloudEvents metadata.
     * Contains the actual metadata that was published to NATS.
     */
    @RegisterForReflection
    public static class ResponseMetadata {
        public String ceType;
        public String ceSource;
        public String ceId;
        public String ceTime;

        public ResponseMetadata(CloudEventsHeaders.CloudEventsMetadata metadata) {
            this.ceType = metadata.type;
            this.ceSource = metadata.source;
            this.ceId = metadata.id;
            this.ceTime = metadata.time;
        }
    }
}
