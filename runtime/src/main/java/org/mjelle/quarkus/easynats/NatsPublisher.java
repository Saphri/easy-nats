package org.mjelle.quarkus.easynats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import jakarta.enterprise.context.Dependent;

/**
 * Generic injectable wrapper for publishing typed messages to NATS JetStream.
 *
 * Supports both primitive types (native encoding) and complex types (Jackson serialization).
 * All messages are published to the hardcoded subject "test".
 *
 * @param <T> the type of payload to publish
 */
@Dependent
public class NatsPublisher<T> {

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for dependency injection.
     *
     * @param connectionManager the NATS connection manager (injected by Quarkus)
     * @param objectMapper the Jackson ObjectMapper (injected by Quarkus)
     */
    NatsPublisher(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a typed payload to the hardcoded NATS subject "test".
     *
     * Supports type-safe publishing with automatic encoding:
     * - Primitive types and String are encoded natively (UTF-8)
     * - Byte arrays are base64-encoded
     * - Complex types are serialized to JSON using Jackson
     *
     * @param payload the object to publish (must not be null)
     * @throws IllegalArgumentException if payload is null
     * @throws SerializationException if serialization fails
     * @throws Exception if publication fails (connection error, broker unreachable, etc.)
     */
    public void publish(T payload) throws Exception {
        if (payload == null) {
            throw new IllegalArgumentException("Cannot publish null object");
        }

        byte[] encodedPayload = encodePayload(payload);
        JetStream jetStream = connectionManager.getJetStream();
        jetStream.publish("test", encodedPayload);
    }

    /**
     * Publishes a typed payload with CloudEvents metadata headers.
     *
     * @param payload the object to publish (must not be null)
     * @param ceType the CloudEvents type (nullable; auto-generated if null)
     * @param ceSource the CloudEvents source (nullable; auto-generated if null)
     * @throws IllegalArgumentException if payload is null
     * @throws SerializationException if serialization fails
     * @throws Exception if publication fails
     */
    public void publishCloudEvent(T payload, String ceType, String ceSource) throws Exception {
        if (payload == null) {
            throw new IllegalArgumentException("Cannot publish null object");
        }

        byte[] encodedPayload = encodePayload(payload);
        Headers headers = CloudEventsHeaders.createHeaders(payload.getClass(), ceType, ceSource);

        JetStream jetStream = connectionManager.getJetStream();
        jetStream.publish("test", headers, encodedPayload);
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
}
