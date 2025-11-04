package org.mjelle.quarkus.easynats;

import jakarta.enterprise.context.ApplicationScoped;

import io.nats.client.Connection;
import io.nats.client.JetStream;

/**
 * Manages the singleton NATS JetStream connection for the entire application.
 *
 * <p>This class consumes the NatsConnection produced by NatsConnectionProvider, providing access to
 * the underlying NATS Connection and JetStream context.
 */
@ApplicationScoped // Note: @ApplicationScoped provides singleton semantics in Quarkus
public class NatsConnectionManager {

  private final Connection connection;
  private final JetStream jetStream;

  public NatsConnectionManager(NatsConnection natsConnection) {
    this.connection = natsConnection.getDelegateConnection();
    try {
      this.jetStream = this.connection.jetStream();
    } catch (Exception e) {
      throw new IllegalStateException(
          "JetStream is not available. NATS connection failed at startup. "
              + "Check that NATS server is running and configuration is correct.");
    }
  }

  /**
   * Returns the shared JetStream connection.
   *
   * @return the JetStream connection
   */
  public JetStream getJetStream() {
    return jetStream;
  }

  /**
   * Returns the shared NATS connection. Do not close this connection; lifecycle is managed by
   * Quarkus.
   *
   * @return the NATS connection
   */
  public Connection getConnection() {
    return connection;
  }
}
