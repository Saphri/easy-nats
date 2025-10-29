package org.mjelle.quarkus.easynats.runtime.health;

import org.jboss.logging.Logger;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;

/**
 * Listener that tracks NATS connection state transitions.
 *
 * This listener is registered with the NATS connection and updates the ConnectionStatusHolder
 * whenever the connection status changes. This enables health checks to report accurate
 * connection status in real-time.
 */
public class NatsConnectionListener implements ConnectionListener {

    private static final Logger LOGGER = Logger.getLogger(NatsConnectionListener.class);

    private final ConnectionStatusHolder statusHolder;

    public NatsConnectionListener(ConnectionStatusHolder statusHolder) {
        this.statusHolder = statusHolder;
    }

    @Override
    public void connectionEvent(Connection conn, Events event) {
        ConnectionStatus newStatus = mapEventToStatus(event);
        // Only update status if event maps to a state change (DISCOVERED_SERVERS returns null for no-op)
        if (newStatus != null) {
            statusHolder.setStatus(newStatus);
            LOGGER.infof("NATS connection status changed: %s", event);
        }
    }

    /**
     * Maps NATS ConnectionListener events to our internal ConnectionStatus.
     *
     * @param event the NATS event
     * @return the corresponding ConnectionStatus, or null if the event should not change state
     */
    private ConnectionStatus mapEventToStatus(Events event) {
        return switch (event) {
            case CONNECTED -> ConnectionStatus.CONNECTED;
            case DISCONNECTED -> ConnectionStatus.DISCONNECTED;
            case RECONNECTED -> ConnectionStatus.RECONNECTED;
            case RESUBSCRIBED -> ConnectionStatus.RESUBSCRIBED;
            case CLOSED -> ConnectionStatus.CLOSED;
            case LAME_DUCK -> ConnectionStatus.LAME_DUCK;
            case DISCOVERED_SERVERS -> null; // Informational event, no state change needed
        };
    }
}
