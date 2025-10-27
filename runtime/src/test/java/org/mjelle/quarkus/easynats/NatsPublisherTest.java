package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for NatsPublisher - Jackson-only encoding.
 *
 * Tests cover:
 * - POJO serialization and CloudEvents wrapping
 * - Null payload rejection
 * - Default subject handling
 * - CloudEvents header verification
 */
@DisplayName("NatsPublisher Tests (Jackson-Only)")
class NatsPublisherTest {

    private ObjectMapper objectMapper;
    private NatsConnectionManager connectionManager;
    private JetStream jetStream;
    private NatsPublisher<TestPojo> pojoPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        connectionManager = mock(NatsConnectionManager.class, withSettings().lenient());
        jetStream = mock(JetStream.class, withSettings().lenient());
        when(connectionManager.getJetStream()).thenReturn(jetStream);

        pojoPublisher = new NatsPublisher<>(connectionManager, objectMapper);
    }

    @Test
    @DisplayName("publish(subject, pojo) serializes POJO to JSON with CloudEvents headers")
    void testPublishPojoAsCloudEvent() throws Exception {
        // Given
        TestPojo pojo = new TestPojo("test_value", 100);

        // When
        pojoPublisher.publish("test.subject", pojo);

        // Then
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("test.subject");

        // Verify CloudEvents headers
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("org.mjelle.quarkus.easynats.NatsPublisherTest.TestPojo");
        assertThat(headers.get("ce-id")).hasSize(1);
        assertThat(headers.get("ce-time")).hasSize(1);
        assertThat(headers.get("ce-source")).hasSize(1);
        assertThat(headers.get("ce-datacontenttype")).hasSize(1).contains("application/json");

        // Verify payload is valid JSON
        TestPojo deserialized = objectMapper.readValue(payloadCaptor.getValue(), TestPojo.class);
        assertThat(deserialized.field1).isEqualTo("test_value");
        assertThat(deserialized.field2).isEqualTo(100);
    }

    @Test
    @DisplayName("publish(subject, null) throws PublishingException")
    void testPublishNullThrowsPublishingException() throws Exception {
        // When / Then
        assertThatThrownBy(() -> pojoPublisher.publish("test.subject", null))
                .isInstanceOf(PublishingException.class)
                .hasMessage("Cannot publish null object");

        verify(jetStream, never()).publish(anyString(), any(byte[].class));
        verify(jetStream, never()).publish(anyString(), any(Headers.class), any(byte[].class));
    }

    @Test
    @DisplayName("publish(pojo) with default subject uses configured subject")
    void testPublishWithDefaultSubject() throws Exception {
        // Given
        TestPojo pojo = new TestPojo("default_value", 50);
        NatsPublisher<TestPojo> publisherWithDefault =
            new NatsPublisher<>(connectionManager, objectMapper, "default.subject");

        // When
        publisherWithDefault.publish(pojo);

        // Then
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Headers> headersCaptor = ArgumentCaptor.forClass(Headers.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(jetStream).publish(subjectCaptor.capture(), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(subjectCaptor.getValue()).isEqualTo("default.subject");

        // Verify CloudEvents headers are present
        Headers headers = headersCaptor.getValue();
        assertThat(headers.get("ce-specversion")).hasSize(1).contains("1.0");
        assertThat(headers.get("ce-type")).hasSize(1).contains("org.mjelle.quarkus.easynats.NatsPublisherTest.TestPojo");
    }

    @Test
    @DisplayName("publish(pojo) without default subject throws PublishingException")
    void testPublishWithoutDefaultSubjectThrowsException() throws Exception {
        // Given
        TestPojo pojo = new TestPojo("test", 1);

        // When / Then
        assertThatThrownBy(() -> pojoPublisher.publish(pojo))
                .isInstanceOf(PublishingException.class)
                .hasMessageContaining("Default NATS subject is not configured");

        verify(jetStream, never()).publish(anyString(), any(byte[].class));
        verify(jetStream, never()).publish(anyString(), any(Headers.class), any(byte[].class));
    }

    @Test
    @DisplayName("publish serializes complex POJO with all fields")
    void testPublishComplexPojo() throws Exception {
        // Given
        ComplexTestPojo complex = new ComplexTestPojo(
                "complex-id",
                new TestPojo("nested", 42),
                42.5);

        NatsPublisher<ComplexTestPojo> complexPublisher =
            new NatsPublisher<>(connectionManager, objectMapper);

        // When
        complexPublisher.publish("complex.subject", complex);

        // Then
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(jetStream).publish(anyString(), any(Headers.class), payloadCaptor.capture());

        ComplexTestPojo deserialized = objectMapper.readValue(payloadCaptor.getValue(), ComplexTestPojo.class);
        assertThat(deserialized.id).isEqualTo("complex-id");
        assertThat(deserialized.innerPojo.field1).isEqualTo("nested");
        assertThat(deserialized.innerPojo.field2).isEqualTo(42);
        assertThat(deserialized.score).isEqualTo(42.5);
    }

    // Helper test classes

    static class TestPojo {
        public String field1;
        public int field2;

        public TestPojo() {
            // Default constructor for Jackson
        }

        public TestPojo(String field1, int field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    static class ComplexTestPojo {
        public String id;
        public TestPojo innerPojo;
        public double score;

        public ComplexTestPojo() {
            // Default constructor for Jackson
        }

        public ComplexTestPojo(String id, TestPojo innerPojo, double score) {
            this.id = id;
            this.innerPojo = innerPojo;
            this.score = score;
        }
    }
}
