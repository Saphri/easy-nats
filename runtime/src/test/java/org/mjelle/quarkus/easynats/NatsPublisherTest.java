package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class NatsPublisherTest {

    @Inject
    ObjectMapper objectMapper;

    private transient NatsConnectionManager connectionManager;
    private transient JetStream jetStream;

    private transient NatsPublisher<String> stringPublisher;
    private transient NatsPublisher<Integer> intPublisher;
    private transient NatsPublisher<TestPojo> pojoPublisher;

    @BeforeEach
    void setUp() {
        connectionManager = mock(NatsConnectionManager.class, withSettings().lenient());
        jetStream = mock(JetStream.class, withSettings().lenient());
        when(connectionManager.getJetStream()).thenReturn(jetStream);

        stringPublisher = new NatsPublisher<>(connectionManager, objectMapper);
        intPublisher = new NatsPublisher<>(connectionManager, objectMapper);
        pojoPublisher = new NatsPublisher<>(connectionManager, objectMapper);
    }

    // Tests for publish() with native types

    @Test
    void testPublishStringEncodesNatively() throws Exception {
        stringPublisher.publish("hello");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("hello");
    }

    @Test
    void testPublishIntegerEncodesNatively() throws Exception {
        intPublisher.publish(42);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("42");
    }

    @Test
    void testPublishNullThrowsIllegalArgumentException() throws Exception {
        assertThatThrownBy(() -> stringPublisher.publish(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Cannot publish null object");

        verify(jetStream, never()).publish(anyString(), any(byte[].class));
    }

    @Test
    void testPublishPojoUsesJacksonEncoder() throws Exception {
        TestPojo pojo = new TestPojo("test_value", 100);
        pojoPublisher.publish(pojo);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");

        // Verify it's valid JSON
        TestPojo deserialized =
            objectMapper.readValue(payloadCaptor.getValue(), 0, payloadCaptor.getValue().length, TestPojo.class);
        assertThat(deserialized.field1).isEqualTo("test_value");
        assertThat(deserialized.field2).isEqualTo(100);
    }

    // Tests for publishCloudEvent()

    @Test
    void testPublishCloudEventWithExplicitMetadata() throws Exception {
        stringPublisher.publishCloudEvent("hello", "com.example.StringEvent", "/test-app");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("hello");

        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-type")).hasSize(1).contains("com.example.StringEvent");
        assertThat(headers.get("ce-source")).hasSize(1).contains("/test-app");
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-id")).hasSize(1);
        assertThat(headers.get("ce-time")).hasSize(1);
    }

    @Test
    void testPublishCloudEventWithNullMetadataAutoGenerates() throws Exception {
        stringPublisher.publishCloudEvent("hello", null, null);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("hello");

        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-type")).hasSize(1).contains("java.lang.String");
        assertThat(headers.get("ce-source")).isNotEmpty();
        assertThat(headers.get("ce-id")).isNotEmpty();
        assertThat(headers.get("ce-time")).isNotEmpty();
    }

    @Test
    void testPublishCloudEventWithNullPayloadThrowsException() throws Exception {
        assertThatThrownBy(() -> stringPublisher.publishCloudEvent(null, "type", "source"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Cannot publish null object");

        verify(jetStream, never()).publish(anyString(), any(Headers.class), any(byte[].class));
    }

    @Test
    void testPublishCloudEventWithPojo() throws Exception {
        TestPojo pojo = new TestPojo("order_123", 99);
        pojoPublisher.publishCloudEvent(pojo, "com.example.OrderEvent", "/orders");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-type")).hasSize(1).contains("com.example.OrderEvent");
        assertThat(headers.get("ce-source")).hasSize(1).contains("/orders");

        // Verify payload is valid JSON
        TestPojo deserialized =
            objectMapper.readValue(payloadCaptor.getValue(), 0, payloadCaptor.getValue().length, TestPojo.class);
        assertThat(deserialized.field1).isEqualTo("order_123");
        assertThat(deserialized.field2).isEqualTo(99);
    }

    // Helper test class
    static class TestPojo {
        public String field1;
        public int field2;

        TestPojo() {
            // Default constructor for Jackson
        }

        TestPojo(String field1, int field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }
}
