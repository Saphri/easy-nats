package org.mjelle.quarkus.easynats.runtime.health;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Thread-safe holder for the current NATS connection status.
 *
 * <p>This class is used by the ConnectionListener to update the connection status and by the health
 * check implementations to query the current status.
 */
@ApplicationScoped
public class ConnectionStatusHolder {

  private final AtomicReference<ConnectionStatus> status =
      new AtomicReference<>(ConnectionStatus.DISCONNECTED);

  /**
   * Gets the current connection status.
   *
   * @return the current connection status
   */
  public ConnectionStatus getStatus() {
    return status.get();
  }

  /**
   * Updates the connection status.
   *
   * @param newStatus the new status
   */
  public void setStatus(ConnectionStatus newStatus) {
    status.set(newStatus);
  }
}
