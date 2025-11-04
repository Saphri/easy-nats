package org.mjelle.quarkus.easynats.runtime.subscriber;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsMessage;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;

/**
 * Unit tests for DefaultNatsMessage<T>.
 *
 * <p>Tests the wrapper implementation that delegates to underlying NATS Message while providing
 * type-safe payload access.
 */
@DisplayName("DefaultNatsMessage Unit Tests")
class DefaultNatsMessageTest {

  private Message mockMessage;
  private Headers mockHeaders;
  private NatsJetStreamMetaData mockMetadata;

  @BeforeEach
  void setUp() {
    mockMessage = mock(Message.class);
    mockHeaders = mock(Headers.class);
    mockMetadata = mock(NatsJetStreamMetaData.class);

    when(mockMessage.getHeaders()).thenReturn(mockHeaders);
    when(mockMessage.getSubject()).thenReturn("test.subject");
    when(mockMessage.metaData()).thenReturn(mockMetadata);
  }

  @Test
  @DisplayName("payload() returns the pre-deserialized payload")
  void testPayloadReturnsDeserializedObject() {
    // Given: A pre-deserialized payload
    String payload = "test-payload";
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, payload);

    // When: We call payload()
    String result = message.payload();

    // Then: We get back the same payload
    assertThat(result).isEqualTo(payload);
  }

  @Test
  @DisplayName("payload() returns different types correctly")
  void testPayloadWorksWithDifferentTypes() {
    // Given: A payload of a custom type
    class OrderData {
      String id;

      OrderData(String id) {
        this.id = id;
      }
    }

    OrderData order = new OrderData("ORD-001");
    NatsMessage<OrderData> message = new DefaultNatsMessage<>(mockMessage, order);

    // When: We call payload()
    OrderData result = message.payload();

    // Then: We get back the same order with correct type
    assertThat(result).isSameAs(order);
    assertThat(result.id).isEqualTo("ORD-001");
  }

  @Test
  @DisplayName("ack() delegates to underlying message")
  void testAckDelegatesToMessage() {
    // Given: A NatsMessage wrapping a mock message
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call ack()
    message.ack();

    // Then: The underlying message ack() was called
    verify(mockMessage, times(1)).ack();
  }

  @Test
  @DisplayName("nak() delegates to underlying message")
  void testNakDelegatesToMessage() {
    // Given: A NatsMessage wrapping a mock message
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call nak()
    message.nak();

    // Then: The underlying message nak() was called
    verify(mockMessage, times(1)).nak();
  }

  @Test
  @DisplayName("nakWithDelay(Duration) delegates to underlying message with same delay")
  void testNakWithDelayDelegatesToMessage() {
    // Given: A NatsMessage wrapping a mock message
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");
    Duration delay = Duration.ofMillis(500);

    // When: We call nakWithDelay with a duration
    message.nakWithDelay(delay);

    // Then: The underlying message nakWithDelay(Duration) was called with same delay
    verify(mockMessage, times(1)).nakWithDelay(delay);
  }

  @Test
  @DisplayName("nakWithDelay(null) delegates null to underlying message")
  void testNakWithDelayNullDelegatesToMessage() {
    // Given: A NatsMessage wrapping a mock message
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call nakWithDelay with null
    message.nakWithDelay(null);

    // Then: The underlying message nakWithDelay(null) was called
    verify(mockMessage, times(1)).nakWithDelay(null);
  }

  @Test
  @DisplayName("term() delegates to underlying message")
  void testTermDelegatesToMessage() {
    // Given: A NatsMessage wrapping a mock message
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call term()
    message.term();

    // Then: The underlying message term() was called
    verify(mockMessage, times(1)).term();
  }

  @Test
  @DisplayName("headers() returns headers from underlying message")
  void testHeadersReturnsUnderlyingHeaders() {
    // Given: A NatsMessage wrapping a mock message with headers
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call headers()
    Headers result = message.headers();

    // Then: We get back the same headers from the underlying message
    assertThat(result).isSameAs(mockHeaders);
    verify(mockMessage, times(1)).getHeaders();
  }

  @Test
  @DisplayName("subject() returns subject from underlying message")
  void testSubjectReturnsUnderlyingSubject() {
    // Given: A NatsMessage wrapping a mock message with subject
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call subject()
    String result = message.subject();

    // Then: We get back the subject from the underlying message
    assertThat(result).isEqualTo("test.subject");
    verify(mockMessage, times(1)).getSubject();
  }

  @Test
  @DisplayName("metadata() returns metadata from underlying message")
  void testMetadataReturnsUnderlyingMetadata() {
    // Given: A NatsMessage wrapping a mock message with metadata
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call metadata()
    NatsJetStreamMetaData result = message.metadata();

    // Then: We get back the metadata from the underlying message
    assertThat(result).isSameAs(mockMetadata);
    verify(mockMessage, times(1)).metaData();
  }

  @Test
  @DisplayName("Multiple method calls work independently")
  void testMultipleMethodCallsAreIndependent() {
    // Given: A NatsMessage
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, "payload");

    // When: We call multiple methods
    String payload = message.payload();
    message.ack();
    String subject = message.subject();
    message.nak();

    // Then: Each method executed without interference
    assertThat(payload).isEqualTo("payload");
    assertThat(subject).isEqualTo("test.subject");
    verify(mockMessage, times(1)).ack();
    verify(mockMessage, times(1)).nak();
  }

  @Test
  @DisplayName("payload() is idempotent - returns same instance on repeated calls")
  void testPayloadIdempotence() {
    // Given: A NatsMessage with a payload
    String payload = "test-payload";
    NatsMessage<String> message = new DefaultNatsMessage<>(mockMessage, payload);

    // When: We call payload() multiple times
    String result1 = message.payload();
    String result2 = message.payload();
    String result3 = message.payload();

    // Then: All calls return the same instance
    assertThat(result1).isSameAs(result2).isSameAs(result3).isSameAs(payload);
  }
}
