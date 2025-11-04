package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.nats.client.*;
import io.nats.client.api.ServerInfo;
import io.nats.client.impl.Headers;

/**
 * Unit tests for NatsConnection wrapper class. Tests delegation to underlying NATS connection and
 * safe close() behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NatsConnection Unit Tests")
class NatsConnectionTest {

  @Mock private Connection mockConnection;

  @Mock private ServerInfo mockServerInfo;

  @Mock private Subscription mockSubscription;

  @Mock private Dispatcher mockDispatcher;

  @Mock private JetStream mockJetStream;

  @Mock private KeyValue mockKeyValue;

  @Mock private KeyValueManagement mockKeyValueManagement;

  @Mock private MessageHandler mockMessageHandler;

  private NatsConnection natsConnection;

  @BeforeEach
  void setUp() {
    natsConnection = new NatsConnection(mockConnection);
  }

  @Test
  @DisplayName("Constructor should reject null delegate")
  void testConstructorRejectsNull() {
    assertThatThrownBy(() -> new NatsConnection(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Delegate connection cannot be null");
  }

  @Test
  @DisplayName("getDelegateConnection should return underlying connection")
  void testGetDelegateConnection() {
    assertThat(natsConnection.getDelegateConnection()).isSameAs(mockConnection);
  }

  @Test
  @DisplayName("close() should be a no-op and not close delegate")
  void testCloseIsNoOp() throws InterruptedException {
    // When
    natsConnection.close();

    // Then - verify delegate.close() was never called
    verify(mockConnection, never()).close();
  }

  @Test
  @DisplayName("publish(subject, data) should delegate to connection")
  void testPublishWithoutHeaders() throws IOException {
    // Given
    String subject = "test.subject";
    byte[] data = "test data".getBytes();

    // When
    natsConnection.publish(subject, data);

    // Then
    verify(mockConnection).publish(subject, data);
  }

  @Test
  @DisplayName("publish(subject, headers, data) should delegate to connection")
  void testPublishWithHeaders() throws IOException {
    // Given
    String subject = "test.subject";
    Headers headers = new Headers();
    headers.add("key", "value");
    byte[] data = "test data".getBytes();

    // When
    natsConnection.publish(subject, headers, data);

    // Then
    verify(mockConnection).publish(subject, headers, data);
  }

  @Test
  @DisplayName("subscribe(subject) should delegate to connection")
  void testSubscribe() throws IOException, JetStreamApiException {
    // Given
    String subject = "test.subject";
    when(mockConnection.subscribe(subject)).thenReturn(mockSubscription);

    // When
    Subscription result = natsConnection.subscribe(subject);

    // Then
    verify(mockConnection).subscribe(subject);
    assertThat(result).isSameAs(mockSubscription);
  }

  @Test
  @DisplayName("createDispatcher should delegate to connection")
  void testCreateDispatcher() {
    // Given
    when(mockConnection.createDispatcher(mockMessageHandler)).thenReturn(mockDispatcher);

    // When
    Dispatcher result = natsConnection.createDispatcher(mockMessageHandler);

    // Then
    verify(mockConnection).createDispatcher(mockMessageHandler);
    assertThat(result).isSameAs(mockDispatcher);
  }

  @Test
  @DisplayName("createJetStreamContext() should delegate to connection")
  void testCreateJetStreamContext() throws IOException {
    // Given
    when(mockConnection.jetStream()).thenReturn(mockJetStream);

    // When
    JetStream result = natsConnection.createJetStreamContext();

    // Then
    verify(mockConnection).jetStream();
    assertThat(result).isSameAs(mockJetStream);
  }

  @Test
  @DisplayName("createJetStreamContext(options) should delegate to connection")
  void testCreateJetStreamContextWithOptions() throws IOException {
    // Given
    JetStreamOptions options = JetStreamOptions.builder().build();
    when(mockConnection.jetStream(options)).thenReturn(mockJetStream);

    // When
    JetStream result = natsConnection.createJetStreamContext(options);

    // Then
    verify(mockConnection).jetStream(options);
    assertThat(result).isSameAs(mockJetStream);
  }

  @Test
  @DisplayName("getServerInfo should delegate to connection")
  void testGetServerInfo() {
    // Given
    when(mockConnection.getServerInfo()).thenReturn(mockServerInfo);

    // When
    ServerInfo result = natsConnection.getServerInfo();

    // Then
    verify(mockConnection).getServerInfo();
    assertThat(result).isSameAs(mockServerInfo);
  }

  @Test
  @DisplayName("getConnectedUrl should delegate to connection")
  void testGetConnectedUrl() {
    // Given
    String expectedUrl = "nats://localhost:4222";
    when(mockConnection.getConnectedUrl()).thenReturn(expectedUrl);

    // When
    String result = natsConnection.getConnectedUrl();

    // Then
    verify(mockConnection).getConnectedUrl();
    assertThat(result).isEqualTo(expectedUrl);
  }

  @Test
  @DisplayName("getStatus should delegate to connection")
  void testGetStatus() {
    // Given
    when(mockConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);

    // When
    Connection.Status result = natsConnection.getStatus();

    // Then
    verify(mockConnection).getStatus();
    assertThat(result).isEqualTo(Connection.Status.CONNECTED);
  }

  @Test
  @DisplayName("keyValue should delegate to connection")
  void testKeyValue() throws IOException, JetStreamApiException {
    // Given
    String bucketName = "test-bucket";
    when(mockConnection.keyValue(bucketName)).thenReturn(mockKeyValue);

    // When
    KeyValue result = natsConnection.keyValue(bucketName);

    // Then
    verify(mockConnection).keyValue(bucketName);
    assertThat(result).isSameAs(mockKeyValue);
  }

  @Test
  @DisplayName("keyValueManagement should delegate to connection")
  void testKeyValueManagement() throws IOException, JetStreamApiException {
    // Given
    when(mockConnection.keyValueManagement()).thenReturn(mockKeyValueManagement);

    // When
    KeyValueManagement result = natsConnection.keyValueManagement();

    // Then
    verify(mockConnection).keyValueManagement();
    assertThat(result).isSameAs(mockKeyValueManagement);
  }

  @Test
  @DisplayName("isClosed should return true when connection is closed")
  void testIsClosedWhenConnectionClosed() {
    // Given
    when(mockConnection.getStatus()).thenReturn(Connection.Status.CLOSED);

    // When
    boolean result = natsConnection.isClosed();

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("isClosed should return false when connection is not closed")
  void testIsClosedWhenConnectionConnected() {
    // Given
    when(mockConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);

    // When
    boolean result = natsConnection.isClosed();

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Multiple concurrent calls should delegate safely")
  void testThreadSafety() throws Exception {
    // Given
    when(mockConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);
    when(mockConnection.getConnectedUrl()).thenReturn("nats://localhost:4222");

    // When - simulate concurrent access
    Thread t1 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                natsConnection.getStatus();
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                natsConnection.getConnectedUrl();
              }
            });

    Thread t3 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                natsConnection.close();
              }
            });

    t1.start();
    t2.start();
    t3.start();

    t1.join();
    t2.join();
    t3.join();

    // Then - verify no exceptions and close was never called
    verify(mockConnection, never()).close();
    verify(mockConnection, atLeast(100)).getStatus();
    verify(mockConnection, atLeast(100)).getConnectedUrl();
  }
}
