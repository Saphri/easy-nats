package org.mjelle.quarkus.easynats.runtime.subscriber;

import static org.assertj.core.api.Assertions.*;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CloudEventUnwrapper")
class CloudEventUnwrapperTest {

    @Test
    @DisplayName("should return payload bytes for valid binary-mode CloudEvent")
    void testValidBinaryModeCloudEvent() {
        // Given
        Headers headers = new Headers();
        headers.add("ce-specversion", "1.0");
        headers.add("ce-type", "com.example.OrderCreated");
        headers.add("ce-source", "/ordering/api");
        headers.add("ce-id", "order-123");
        headers.add("ce-time", "2025-10-27T10:30:00Z");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When
        byte[] result = CloudEventUnwrapper.unwrapData(message);

        // Then
        assertThat(result).isEqualTo(payload);
    }

    @Test
    @DisplayName("should throw CloudEventException when ce-specversion header is missing")
    void testMissingSpecVersion() {
        // Given
        Headers headers = new Headers();
        headers.add("ce-type", "com.example.OrderCreated");
        headers.add("ce-source", "/ordering/api");
        headers.add("ce-id", "order-123");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When / Then
        assertThatThrownBy(() -> CloudEventUnwrapper.unwrapData(message))
                .isInstanceOf(CloudEventException.class)
                .hasMessageContaining("ce-specversion");
    }

    @Test
    @DisplayName("should throw CloudEventException when ce-type header is missing")
    void testMissingType() {
        // Given
        Headers headers = new Headers();
        headers.add("ce-specversion", "1.0");
        headers.add("ce-source", "/ordering/api");
        headers.add("ce-id", "order-123");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When / Then
        assertThatThrownBy(() -> CloudEventUnwrapper.unwrapData(message))
                .isInstanceOf(CloudEventException.class)
                .hasMessageContaining("ce-type");
    }

    @Test
    @DisplayName("should throw CloudEventException when ce-source header is missing")
    void testMissingSource() {
        // Given
        Headers headers = new Headers();
        headers.add("ce-specversion", "1.0");
        headers.add("ce-type", "com.example.OrderCreated");
        headers.add("ce-id", "order-123");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When / Then
        assertThatThrownBy(() -> CloudEventUnwrapper.unwrapData(message))
                .isInstanceOf(CloudEventException.class)
                .hasMessageContaining("ce-source");
    }

    @Test
    @DisplayName("should throw CloudEventException when ce-id header is missing")
    void testMissingId() {
        // Given
        Headers headers = new Headers();
        headers.add("ce-specversion", "1.0");
        headers.add("ce-type", "com.example.OrderCreated");
        headers.add("ce-source", "/ordering/api");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When / Then
        assertThatThrownBy(() -> CloudEventUnwrapper.unwrapData(message))
                .isInstanceOf(CloudEventException.class)
                .hasMessageContaining("ce-id");
    }

    @Test
    @DisplayName("should throw CloudEventException when ce-specversion is not 1.0")
    void testInvalidSpecVersion() {
        // Given
        Headers headers = new Headers();
        headers.add("ce-specversion", "2.0");
        headers.add("ce-type", "com.example.OrderCreated");
        headers.add("ce-source", "/ordering/api");
        headers.add("ce-id", "order-123");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When / Then
        assertThatThrownBy(() -> CloudEventUnwrapper.unwrapData(message))
                .isInstanceOf(CloudEventException.class)
                .hasMessageContaining("1.0");
    }

    @Test
    @DisplayName("should extract correct payload bytes for multiple different messages")
    void testMultipleMessagesWithDifferentPayloads() {
        // Given
        Headers headers1 = new Headers();
        headers1.add("ce-specversion", "1.0");
        headers1.add("ce-type", "com.example.OrderCreated");
        headers1.add("ce-source", "/ordering/api");
        headers1.add("ce-id", "order-123");

        byte[] payload1 = "{\"orderId\": \"ORD-001\"}".getBytes(StandardCharsets.UTF_8);
        Message message1 = new NatsMessage("orders.created", null, headers1, payload1);

        Headers headers2 = new Headers();
        headers2.add("ce-specversion", "1.0");
        headers2.add("ce-type", "com.example.OrderCreated");
        headers2.add("ce-source", "/ordering/api");
        headers2.add("ce-id", "order-456");

        byte[] payload2 = "{\"orderId\": \"ORD-002\", \"status\": \"PENDING\"}".getBytes(StandardCharsets.UTF_8);
        Message message2 = new NatsMessage("orders.created", null, headers2, payload2);

        // When
        byte[] result1 = CloudEventUnwrapper.unwrapData(message1);
        byte[] result2 = CloudEventUnwrapper.unwrapData(message2);

        // Then
        assertThat(result1).isEqualTo(payload1);
        assertThat(result2).isEqualTo(payload2);
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("should handle optional CloudEvent headers gracefully")
    void testOptionalHeadersPresent() {
        // Given - optional headers like ce-time, ce-datacontenttype
        Headers headers = new Headers();
        headers.add("ce-specversion", "1.0");
        headers.add("ce-type", "com.example.OrderCreated");
        headers.add("ce-source", "/ordering/api");
        headers.add("ce-id", "order-123");
        headers.add("ce-time", "2025-10-27T10:30:00Z");
        headers.add("ce-datacontenttype", "application/json");
        headers.add("custom-header", "custom-value");

        byte[] payload = "{\"orderId\": \"ORD-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new NatsMessage("orders.created", null, headers, payload);

        // When
        byte[] result = CloudEventUnwrapper.unwrapData(message);

        // Then - optional headers should not affect extraction
        assertThat(result).isEqualTo(payload);
    }
}
