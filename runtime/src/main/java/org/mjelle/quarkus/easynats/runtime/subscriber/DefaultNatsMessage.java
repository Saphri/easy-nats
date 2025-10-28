package org.mjelle.quarkus.easynats.runtime.subscriber;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;

import org.mjelle.quarkus.easynats.NatsMessage;

import java.time.Duration;

/**
 * Default implementation of NatsMessage&lt;T&gt;.
 *
 * Wraps a NATS JetStream message and provides type-safe access to a pre-deserialized payload.
 * All control methods (ack, nak, term) are direct pass-through delegations to the underlying NATS message.
 * The payload is deserialized at construction time, not lazily on payload() call.
 *
 * @param <T> Type of the deserialized message payload
 */
public class DefaultNatsMessage<T> implements NatsMessage<T> {

    private final Message underlyingMessage;
    private final T payload;

    /**
     * Create a new NatsMessage wrapper with pre-deserialized payload.
     *
     * @param underlyingMessage The underlying NATS JetStream message
     * @param payload The pre-deserialized typed payload (must not be null)
     */
    public DefaultNatsMessage(Message underlyingMessage, T payload) {
        this.underlyingMessage = underlyingMessage;
        this.payload = payload;
    }

    @Override
    public T payload() {
        return payload;
    }

    @Override
    public void ack() {
        underlyingMessage.ack();
    }

    @Override
    public void nak() {
        underlyingMessage.nak();
    }

    @Override
    public void nakWithDelay(Duration delay) {
        underlyingMessage.nakWithDelay(delay);
    }

    @Override
    public void term() {
        underlyingMessage.term();
    }

    @Override
    public Headers headers() {
        return underlyingMessage.getHeaders();
    }

    @Override
    public String subject() {
        return underlyingMessage.getSubject();
    }

    @Override
    public NatsJetStreamMetaData metadata() {
        return underlyingMessage.metaData();
    }
}
