package org.mjelle.quarkus.easynats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import jakarta.enterprise.context.Dependent;

/**
 * Generic injectable wrapper for publishing typed messages to NATS JetStream.
 *
 * Supports both primitive types (native encoding) and complex types (Jackson serialization).
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
     * Publishes a typed payload to the default NATS subject.
     * The default subject must be configured via @NatsSubject.
     *
     * @param payload the object to publish (must not be null)
     * @throws IllegalStateException if the default subject is not configured
     * @throws IllegalArgumentException if payload is null
     * @throws SerializationException if serialization fails
     * @throws Exception if publication fails (connection error, broker unreachable, etc.)
     */
    public void publish(T payload) throws Exception {
        validateDefaultSubject();
        publish(this.subject, payload);
    }

    /**
     * Publishes a typed payload to the specified NATS subject.
     *
     * @param subject the NATS subject to publish to
     * @param payload the object to publish (must not be null)
     * @throws IllegalArgumentException if payload is null
     * @throws SerializationException if serialization fails
     * @throws Exception if publication fails (connection error, broker unreachable, etc.)
     */
    public void publish(String subject, T payload) throws Exception {
        if (payload == null) {
            throw new IllegalArgumentException("Cannot publish null object");
        }

        byte[] encodedPayload = encodePayload(payload);
        JetStream jetStream = connectionManager.getJetStream();
        jetStream.publish(subject, encodedPayload);
    }

    /**
     * Publishes a typed payload with CloudEvents metadata headers to the default subject.
     *
     * @param payload the object to publish (must not be null)
     * @param ceType the CloudEvents type (nullable; auto-generated if null)
     * @param ceSource the CloudEvents source (nullable; auto-generated if null)
     * @return the CloudEventsMetadata that was published (includes generated ce-id and ce-time)
     * @throws IllegalStateException if the default subject is not configured
     * @throws IllegalArgumentException if payload is null
     * @throws SerializationException if serialization fails
     * @throws Exception if publication fails
     */
    public CloudEventsHeaders.CloudEventsMetadata publishCloudEvent(T payload, String ceType, String ceSource) throws Exception {
        validateDefaultSubject();
        return publishCloudEvent(this.subject, payload, ceType, ceSource);
    }

    /**
     * Publishes a typed payload with CloudEvents metadata headers to the specified subject.
     *
     * @param subject the NATS subject to publish to
     * @param payload the object to publish (must not be null)
     * @param ceType the CloudEvents type (nullable; auto-generated if null)
     * @param ceSource the CloudEvents source (nullable; auto-generated if null)
     * @return the CloudEventsMetadata that was published (includes generated ce-id and ce-time)
     * @throws IllegalArgumentException if payload is null
     * @throws SerializationException if serialization fails
     * @throws Exception if publication fails
     */
    public CloudEventsHeaders.CloudEventsMetadata publishCloudEvent(String subject, T payload, String ceType, String ceSource) throws Exception {
        if (payload == null) {
            throw new IllegalArgumentException("Cannot publish null object");
        }

        byte[] encodedPayload = encodePayload(payload);
        CloudEventsHeaders.HeadersWithMetadata hwm = CloudEventsHeaders.createHeadersWithMetadata(
            payload.getClass(), ceType, ceSource);

        JetStream jetStream = connectionManager.getJetStream();
        jetStream.publish(subject, hwm.headers, encodedPayload);

        return hwm.metadata;
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
     * @throws IllegalStateException if the default subject is not configured
     */
    private void validateDefaultSubject() {
        if (this.subject == null || this.subject.trim().isEmpty()) {
            throw new IllegalStateException("Default NATS subject is not configured for this publisher. Use @NatsSubject or provide the subject dynamically.");
        }
    }
}
