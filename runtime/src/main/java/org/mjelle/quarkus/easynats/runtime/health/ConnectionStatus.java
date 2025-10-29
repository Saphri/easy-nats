package org.mjelle.quarkus.easynats.runtime.health;

/**
 * Represents the current status of the NATS connection.
 *
 * This enum tracks the various states the NATS connection can be in:
 * - CONNECTED: The NATS connection is active and operational
 * - DISCONNECTED: The connection has been lost; attempting to reconnect
 * - RECONNECTING: Actively attempting to re-establish the connection
 * - RECONNECTED: Successfully reconnected after a disconnection
 * - RESUBSCRIBED: Subscriptions have been restored after reconnection
 * - CLOSED: The connection has been permanently closed
 * - LAME_DUCK: Graceful shutdown initiated by the NATS server
 */
public enum ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    RECONNECTED,
    RESUBSCRIBED,
    CLOSED,
    LAME_DUCK
}
