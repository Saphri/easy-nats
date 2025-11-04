package org.mjelle.quarkus.easynats;

import io.nats.client.*;
import io.nats.client.api.ServerInfo;
import io.nats.client.impl.Headers;

/**
 * Thin facade wrapper around io.nats.client.Connection that delegates all operations to the
 * underlying connection but provides a safe close() method that is a no-op.
 *
 * <p>The lifecycle of the underlying connection is managed by the extension itself, so user code
 * should never close the connection directly. This wrapper ensures that accidental close() calls do
 * not terminate the shared connection.
 */
public class NatsConnection implements AutoCloseable {

  private final Connection delegate;

  /**
   * Public constructor to wrap an existing connection. This is public to allow runtime package
   * classes to create instances.
   *
   * @param delegate the underlying NATS connection
   */
  public NatsConnection(Connection delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException("Delegate connection cannot be null");
    }
    this.delegate = delegate;
  }

  /**
   * No-op close method that intentionally does nothing. The underlying connection lifecycle is
   * managed by the extension. This method exists to satisfy AutoCloseable but will never close the
   * delegate.
   */
  @Override
  public void close() {
    // Intentional no-op - lifecycle managed by extension
  }

  /**
   * Returns the underlying delegate connection for internal use and testing.
   *
   * <p>WARNING: Do not close the returned connection. This is for internal use only.
   *
   * @return the underlying NATS connection
   */
  public Connection getDelegateConnection() {
    return delegate;
  }

  /**
   * Publishes a message to the specified subject.
   *
   * @param subject the subject to publish to
   * @param data the message payload
   * @throws java.io.IOException if an I/O error occurs
   */
  public void publish(String subject, byte[] data) throws java.io.IOException {
    delegate.publish(subject, data);
  }

  /**
   * Publishes a message with headers to the specified subject.
   *
   * @param subject the subject to publish to
   * @param headers the message headers
   * @param data the message payload
   * @throws java.io.IOException if an I/O error occurs
   */
  public void publish(String subject, Headers headers, byte[] data) throws java.io.IOException {
    delegate.publish(subject, headers, data);
  }

  /**
   * Creates a synchronous subscription to the specified subject.
   *
   * @param subject the subject to subscribe to
   * @return a subscription that can be used to receive messages
   * @throws java.io.IOException if an I/O error occurs
   * @throws io.nats.client.JetStreamApiException if a JetStream API error occurs
   */
  public Subscription subscribe(String subject) throws java.io.IOException, JetStreamApiException {
    return delegate.subscribe(subject);
  }

  /**
   * Creates a dispatcher for asynchronous message handling.
   *
   * @param handler the message handler to process incoming messages
   * @return a dispatcher that can be used to subscribe to subjects
   */
  public Dispatcher createDispatcher(MessageHandler handler) {
    return delegate.createDispatcher(handler);
  }

  /**
   * Creates a JetStream context with default options.
   *
   * @return a JetStream context for working with JetStream features
   * @throws java.io.IOException if an I/O error occurs
   */
  public JetStream createJetStreamContext() throws java.io.IOException {
    return delegate.jetStream();
  }

  /**
   * Creates a JetStream context with custom options.
   *
   * @param options the JetStream options
   * @return a JetStream context for working with JetStream features
   * @throws java.io.IOException if an I/O error occurs
   */
  public JetStream createJetStreamContext(JetStreamOptions options) throws java.io.IOException {
    return delegate.jetStream(options);
  }

  /**
   * Returns information about the connected NATS server.
   *
   * @return server information
   */
  public ServerInfo getServerInfo() {
    return delegate.getServerInfo();
  }

  /**
   * Returns the URL of the currently connected server.
   *
   * @return the connected server URL
   */
  public String getConnectedUrl() {
    return delegate.getConnectedUrl();
  }

  /**
   * Returns the current connection status.
   *
   * @return the connection status
   */
  public Connection.Status getStatus() {
    return delegate.getStatus();
  }

  /**
   * Creates a key-value store context for the specified bucket.
   *
   * @param bucketName the name of the key-value bucket
   * @return a key-value store context
   * @throws java.io.IOException if an I/O error occurs
   * @throws io.nats.client.JetStreamApiException if a JetStream API error occurs
   */
  public KeyValue keyValue(String bucketName) throws java.io.IOException, JetStreamApiException {
    return delegate.keyValue(bucketName);
  }

  /**
   * Creates a key-value management context for creating and managing buckets.
   *
   * @return a key-value management context
   * @throws java.io.IOException if an I/O error occurs
   * @throws io.nats.client.JetStreamApiException if a JetStream API error occurs
   */
  public KeyValueManagement keyValueManagement() throws java.io.IOException, JetStreamApiException {
    return delegate.keyValueManagement();
  }

  /**
   * Checks if the connection is closed.
   *
   * @return true if the connection is closed, false otherwise
   */
  public boolean isClosed() {
    return delegate.getStatus() == Connection.Status.CLOSED;
  }
}
