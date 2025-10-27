package org.mjelle.quarkus.easynats;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import jakarta.enterprise.context.Dependent;

/**
 * Generic injectable wrapper for publishing typed messages to NATS JetStream as CloudEvents.
 *
 * All messages published through this class are automatically wrapped in the CloudEvents 1.0 format.
 * Supports both primitive types (native encoding) and complex types (Jackson serialization) for the
 * payload. The CloudEvents metadata is generated automatically and includes:
 * - ce-specversion: "1.0"
 * - ce-type: The fully-qualified class name of the payload
 * - ce-source: The application name (from quarkus.application.name), hostname, or "localhost"
 * - ce-id: Auto-generated UUID
 * - ce-time: Current timestamp in ISO 8601 UTC format
 * - ce-datacontenttype: "application/json"
 *
 * @param <T> the type of payload to publish
 */
@Dependent
public class NatsPublisher<T> {

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final String subject;

    /**
     * Constructor for dependency injection.
     *
     * @param connectionManager the NATS connection manager (injected by Quarkus)
     * @param objectMapper the Jackson ObjectMapper (injected by Quarkus)
     */
    public NatsPublisher(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this(connectionManager, objectMapper, null);
    }

    /**
     * Constructor for dependency injection with a default subject.
     *
     * @param connectionManager the NATS connection manager (injected by Quarkus)
     * @param objectMapper the Jackson ObjectMapper (injected by Quarkus)
     * @param subject the default NATS subject for this publisher
     */
    public NatsPublisher(NatsConnectionManager connectionManager, ObjectMapper objectMapper, String subject) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.subject = subject;
    }

    /**
     * Publishes a typed payload to the default NATS subject as a CloudEvent.
     * The default subject must be configured via @NatsSubject.
     *
     * The payload is automatically wrapped in CloudEvents 1.0 format with:
     * - ce-specversion: "1.0"
     * - ce-type: The fully-qualified class name of the payload
     * - ce-source: The application name (from quarkus.application.name), hostname, or "localhost"
     * - ce-id: Auto-generated UUID
     * - ce-time: Current timestamp in ISO 8601 UTC format
     * - ce-datacontenttype: "application/json"
     *
     * @param payload the object to publish (must not be null)
     * @throws IllegalArgumentException if payload is null
     * @throws PublishingException if the default subject is not configured or if publication fails
     */
    public void publish(T payload) throws PublishingException {
        validateDefaultSubject();
        publish(this.subject, payload);
    }

    /**
     * Publishes a typed payload to the specified NATS subject as a CloudEvent.
     *
     * The payload is automatically wrapped in CloudEvents 1.0 format with:
     * - ce-specversion: "1.0"
     * - ce-type: The fully-qualified class name of the payload
     * - ce-source: The application name (from quarkus.application.name), hostname, or "localhost"
     * - ce-id: Auto-generated UUID
     * - ce-time: Current timestamp in ISO 8601 UTC format
     * - ce-datacontenttype: "application/json"
     *
     * @param subject the NATS subject to publish to
     * @param payload the object to publish (must not be null)
     * @throws PublishingException if publication fails (payload is null, serialization error, connection error, broker unreachable, etc.)
     */
    public void publish(String subject, T payload) throws PublishingException {
        if (payload == null) {
            throw new PublishingException("Cannot publish null object");
        }

        try {
            byte[] encodedPayload = encodePayload(payload);
            CloudEventsHeaders.HeadersWithMetadata hwm = CloudEventsHeaders.createHeadersWithMetadata(
                payload.getClass(), null, null);

            JetStream jetStream = connectionManager.getJetStream();
            jetStream.publish(subject, hwm.headers, encodedPayload);
        } catch (IOException | JetStreamApiException | SerializationException e) {
            // Wrap other exceptions (NATS connection, broker errors, etc.)
            throw new PublishingException("Failed to publish message to subject '" + subject + "'", e);
        }
    }


    /**
     * Encode a payload using the appropriate encoder strategy.
     *
     * @param payload the object to encode
     * @return the encoded byte array
     * @throws SerializationException if Jackson encoding fails
     */
    private byte[] encodePayload(T payload) throws SerializationException {
        Class<?> payloadClass = payload.getClass();
        TypedPayloadEncoder.PayloadEncoderStrategy strategy =
            TypedPayloadEncoder.resolveEncoder(payloadClass);

        if (strategy == TypedPayloadEncoder.PayloadEncoderStrategy.NATIVE_ENCODER) {
            return TypedPayloadEncoder.encodeNatively(payload);
        } else {
            return TypedPayloadEncoder.encodeWithJackson(payload, objectMapper);
        }
    }

    /**
     * Validates that a default subject is configured for this publisher.
     *
     * @throws PublishingException if the default subject is not configured
     */
    private void validateDefaultSubject() throws PublishingException {
        if (this.subject == null || this.subject.trim().isEmpty()) {
            throw new PublishingException("Default NATS subject is not configured for this publisher. Use @NatsSubject or provide the subject dynamically.");
        }
    }
}
