package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import jakarta.inject.Inject;
import java.util.Base64;
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
    private transient NatsPublisher<byte[]> byteArrayPublisher;
    private transient NatsPublisher<TestPojo> pojoPublisher;

    @BeforeEach
    void setUp() {
        connectionManager = mock(NatsConnectionManager.class, withSettings().lenient());
        jetStream = mock(JetStream.class, withSettings().lenient());
        when(connectionManager.getJetStream()).thenReturn(jetStream);

        stringPublisher = new NatsPublisher<>(connectionManager, objectMapper);
        intPublisher = new NatsPublisher<>(connectionManager, objectMapper);
        byteArrayPublisher = new NatsPublisher<>(connectionManager, objectMapper);
        pojoPublisher = new NatsPublisher<>(connectionManager, objectMapper);
    }

    // Tests for publish() with transparent CloudEvents

    @Test
    void testPublishStringAsCloudEvent() throws Exception {
        stringPublisher.publish("test", "hello");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("hello");

        // Verify CloudEvents headers
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("java.lang.String");
        assertThat(headers.get("ce-source")).hasSize(1);
        assertThat(headers.get("ce-id")).hasSize(1);
        assertThat(headers.get("ce-time")).hasSize(1);
        assertThat(headers.get("ce-datacontenttype")).hasSize(1).contains("application/json");
    }

    @Test
    void testPublishIntegerAsCloudEvent() throws Exception {
        intPublisher.publish("test", 42);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("42");

        // Verify CloudEvents headers
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("java.lang.Integer");
        assertThat(headers.get("ce-id")).hasSize(1);
        assertThat(headers.get("ce-time")).hasSize(1);
    }

    @Test
    void testPublishByteArrayWithBase64Encoding() throws Exception {
        byte[] testData = new byte[]{0x48, 0x65, 0x6c, 0x6c, 0x6f}; // "Hello"
        byteArrayPublisher.publish("test", testData);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");

        // Verify the payload is base64-encoded
        String publishedPayload = new String(payloadCaptor.getValue());
        String expectedBase64 = Base64.getEncoder().encodeToString(testData);
        assertThat(publishedPayload).isEqualTo(expectedBase64);
        assertThat(publishedPayload).isEqualTo("SGVsbG8="); // Base64 of "Hello"

        // Verify CloudEvents headers
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("byte[]");
        assertThat(headers.get("ce-id")).hasSize(1);
        assertThat(headers.get("ce-time")).hasSize(1);
        assertThat(headers.get("ce-source")).hasSize(1);
    }

    @Test
    void testPublishNullThrowsIllegalArgumentException() throws Exception {
        assertThatThrownBy(() -> stringPublisher.publish("test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Cannot publish null object");

        verify(jetStream, never()).publish(anyString(), any(byte[].class));
        verify(jetStream, never()).publish(anyString(), any(Headers.class), any(byte[].class));
    }

    @Test
    void testPublishPojoAsCloudEvent() throws Exception {
        TestPojo pojo = new TestPojo("test_value", 100);
        pojoPublisher.publish("test", pojo);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test");

        // Verify CloudEvents headers
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("org.mjelle.quarkus.easynats.NatsPublisherTest.TestPojo");
        assertThat(headers.get("ce-id")).hasSize(1);
        assertThat(headers.get("ce-time")).hasSize(1);
        assertThat(headers.get("ce-source")).hasSize(1);

        // Verify payload is valid JSON
        TestPojo deserialized =
            objectMapper.readValue(payloadCaptor.getValue(), 0, payloadCaptor.getValue().length, TestPojo.class);
        assertThat(deserialized.field1).isEqualTo("test_value");
        assertThat(deserialized.field2).isEqualTo(100);
    }

    @Test
    void testPublishWithDefaultSubject() throws Exception {
        NatsPublisher<String> publisherWithDefault = new NatsPublisher<>(connectionManager, objectMapper, "default.subject");
        publisherWithDefault.publish("hello");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("default.subject");
        assertThat(new String(payloadCaptor.getValue())).isEqualTo("hello");

        // Verify CloudEvents headers are present
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("java.lang.String");
    }

    @Test
    void testPublishWithDefaultSubjectNotConfiguredThrowsException() throws Exception {
        assertThatThrownBy(() -> stringPublisher.publish("hello"))
            .isInstanceOf(PublishingException.class)
            .hasMessageContaining("Default NATS subject is not configured");

        verify(jetStream, never()).publish(anyString(), any(byte[].class));
        verify(jetStream, never()).publish(anyString(), any(Headers.class), any(byte[].class));
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
