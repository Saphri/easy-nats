package org.mjelle.quarkus.easynats.runtime.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import org.mjelle.quarkus.easynats.runtime.NatsConfiguration;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

/**
 * Unit tests for DefaultMessageHandler parameter type detection and codec error handling.
 *
 * <p>
 * Tests the core framework logic that detects explicit vs implicit acknowledgment mode
 * based on subscriber method parameter types, and validates codec deserialization error
 * handling behavior.
 * </p>
 */
@DisplayName("DefaultMessageHandler Tests")
@ExtendWith(MockitoExtension.class)
class DefaultMessageHandlerTest {

    private ObjectMapper objectMapper;
    @Mock(lenient = true) private SubscriberMetadata metadata;
    @Mock(lenient = true) private Codec codec;
    @Mock(lenient = true) private NatsConfiguration config;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Default mock setup (lenient allows unused stubs in parameter type detection tests)
        when(metadata.subject()).thenReturn("test.subject");
        when(metadata.methodName()).thenReturn("handleMessage");
        when(metadata.isDurableConsumer()).thenReturn(false);
        when(config.logPayloadsOnError()).thenReturn(false);
    }

    private Message createCloudEventMessage(byte[] payload) {
        Headers headers = new Headers();
        headers.add("ce-specversion", "1.0");
        headers.add("ce-type", "com.example.Order");
        headers.add("ce-source", "/test");
        headers.add("ce-id", "test-123");
        return new NatsMessage("test.subject", null, headers, payload);
    }

    // Sample types for testing
    static class OrderData {
        String id;
    }

    static class TestSubscriber {
        // Explicit mode: NatsMessage<T>
        public void handleWithExplicitMode(org.mjelle.quarkus.easynats.NatsMessage<OrderData> msg) {}

        // Implicit mode: typed payload only
        public void handleWithImplicitMode(OrderData order) {}

        // Implicit mode: String payload
        public void handleWithString(String message) {}
    }

    @Test
    @DisplayName("isNatsMessageType() returns true for NatsMessage<T> parameter")
    void testIsNatsMessageTypeWithGenericType() throws Exception {
        // Given: A method with NatsMessage<T> parameter
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", org.mjelle.quarkus.easynats.NatsMessage.class);
        Type paramType = method.getGenericParameterTypes()[0];

        // When: We check if it's a NatsMessage type
        boolean isNatsMessage = isNatsMessageType(paramType);

        // Then: It should return true
        assertThat(isNatsMessage).isTrue();
    }

    @Test
    @DisplayName("isNatsMessageType() returns false for typed payload parameter")
    void testIsNatsMessageTypeWithTypedPayload() throws Exception {
        // Given: A method with typed payload parameter (not NatsMessage)
        Method method = TestSubscriber.class.getMethod("handleWithImplicitMode", OrderData.class);
        Type paramType = method.getGenericParameterTypes()[0];

        // When: We check if it's a NatsMessage type
        boolean isNatsMessage = isNatsMessageType(paramType);

        // Then: It should return false
        assertThat(isNatsMessage).isFalse();
    }

    @Test
    @DisplayName("isNatsMessageType() returns false for String parameter")
    void testIsNatsMessageTypeWithString() throws Exception {
        // Given: A method with String parameter
        Method method = TestSubscriber.class.getMethod("handleWithString", String.class);
        Type paramType = method.getGenericParameterTypes()[0];

        // When: We check if it's a NatsMessage type
        boolean isNatsMessage = isNatsMessageType(paramType);

        // Then: It should return false
        assertThat(isNatsMessage).isFalse();
    }

    @Test
    @DisplayName("extractPayloadType() extracts T from NatsMessage<T>")
    void testExtractPayloadTypeFromGeneric() throws Exception {
        // Given: A method with NatsMessage<OrderData> parameter
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", org.mjelle.quarkus.easynats.NatsMessage.class);
        Type paramType = method.getGenericParameterTypes()[0];

        // When: We extract the payload type
        JavaType payloadType = extractPayloadType(paramType);

        // Then: We get OrderData type
        assertThat(payloadType.getRawClass()).isEqualTo(OrderData.class);
    }

    @Test
    @DisplayName("extractPayloadType() returns String fallback if type parameter missing")
    void testExtractPayloadTypeFallback() {
        // Given: A raw NatsMessage type (no type parameter)
        Type rawType = org.mjelle.quarkus.easynats.NatsMessage.class;

        // When: We extract the payload type
        JavaType payloadType = extractPayloadType(rawType);

        // Then: We get String as fallback
        assertThat(payloadType.getRawClass()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Explicit mode detection: NatsMessage<T> parameter")
    void testExplicitModeDetectionWithNatsMessage() throws Exception {
        // Given: A method with NatsMessage<OrderData> parameter
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", org.mjelle.quarkus.easynats.NatsMessage.class);
        Type paramType = method.getGenericParameterTypes()[0];

        // When: We check parameter type
        boolean isExplicit = isNatsMessageType(paramType);
        JavaType payloadType = extractPayloadType(paramType);

        // Then: Framework detects explicit mode and extracts OrderData as payload type
        assertThat(isExplicit).isTrue();
        assertThat(payloadType.getRawClass()).isEqualTo(OrderData.class);
    }

    @Test
    @DisplayName("Implicit mode detection: typed payload parameter")
    void testImplicitModeDetectionWithTypedPayload() throws Exception {
        // Given: A method with OrderData parameter
        Method method = TestSubscriber.class.getMethod("handleWithImplicitMode", OrderData.class);
        Type paramType = method.getGenericParameterTypes()[0];

        // When: We check parameter type
        boolean isExplicit = isNatsMessageType(paramType);

        // Then: Framework detects implicit mode (not explicit)
        assertThat(isExplicit).isFalse();
    }

    // ============== Codec Error Handling Tests (US2) ==============

    @Test
    @DisplayName("Implicit mode: codec decode error causes NAK and subscriber NOT invoked")
    void testImplicitModeCodecDecodeErrorNaksMessage() throws Exception {
        // Given: Implicit mode subscriber with codec that throws DeserializationException
        Method method = TestSubscriber.class.getMethod("handleWithImplicitMode", OrderData.class);
        TestSubscriber bean = spy(new TestSubscriber());

        byte[] messageData = "{\"id\":\"ORDER-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = createCloudEventMessage(messageData);

        // Mock codec to throw exception
        when(codec.decode(messageData, OrderData.class, "com.example.Order"))
            .thenThrow(new DeserializationException("Codec validation failed"));

        DefaultMessageHandler handler = new DefaultMessageHandler(
            metadata, bean, method, objectMapper, codec, config);

        // When: Handler processes message with codec error
        handler.handle(message);

        // Then: Subscriber method is NOT invoked when codec fails in implicit mode
        verify(bean, never()).handleWithImplicitMode(any());
    }

    @Test
    @DisplayName("Implicit mode: successful codec decode calls subscriber and ACKs")
    void testImplicitModeCodecDecodeSuccessAcksMessage() throws Exception {
        // Given: Implicit mode subscriber with successful codec decode
        Method method = TestSubscriber.class.getMethod("handleWithImplicitMode", OrderData.class);
        TestSubscriber bean = spy(new TestSubscriber());

        byte[] messageData = "{\"id\":\"ORDER-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = spy(createCloudEventMessage(messageData));

        // Mock codec to return decoded object
        OrderData decodedData = new OrderData();
        decodedData.id = "ORDER-123";
        when(codec.decode(messageData, OrderData.class, "com.example.Order"))
            .thenReturn(decodedData);

        DefaultMessageHandler handler = new DefaultMessageHandler(
            metadata, bean, method, objectMapper, codec, config);

        // When: Handler processes message with successful decode
        handler.handle(message);

        // Then: Message is ACK'd and subscriber method IS invoked
        verify(message).ack();
        verify(bean).handleWithImplicitMode(decodedData);
    }

    @Test
    @DisplayName("Explicit mode: codec decode error auto-NAKs (consistent with implicit mode)")
    void testExplicitModeCodecDecodeErrorAutoNak() throws Exception {
        // Given: Explicit mode subscriber with codec that throws DeserializationException
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", org.mjelle.quarkus.easynats.NatsMessage.class);
        TestSubscriber bean = spy(new TestSubscriber());

        byte[] messageData = "{\"id\":\"ORDER-123\"}".getBytes(StandardCharsets.UTF_8);
        Message message = spy(createCloudEventMessage(messageData));

        // Mock codec to throw exception
        when(codec.decode(messageData, OrderData.class, "com.example.Order"))
            .thenThrow(new DeserializationException("Codec validation failed"));

        DefaultMessageHandler handler = new DefaultMessageHandler(
            metadata, bean, method, objectMapper, codec, config);

        // When: Handler processes message with codec error in explicit mode
        handler.handle(message);

        // Then: Message IS auto-NAK'd (same behavior as implicit mode, since subscriber is never invoked)
        verify(message).nak();
        verify(message, never()).ack();
        // Subscriber method is NOT invoked (error before method call)
        verify(bean, never()).handleWithExplicitMode(any());
    }

    // Helper methods extracted from DefaultMessageHandler
    private boolean isNatsMessageType(Type type) {
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            return rawType instanceof Class && org.mjelle.quarkus.easynats.NatsMessage.class.isAssignableFrom((Class<?>) rawType);
        } else if (type instanceof Class) {
            return org.mjelle.quarkus.easynats.NatsMessage.class.isAssignableFrom((Class<?>) type);
        }
        return false;
    }

    private JavaType extractPayloadType(Type type) {
        if (type instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) type).getActualTypeArguments();
            if (typeArgs.length > 0) {
                return objectMapper.getTypeFactory().constructType(typeArgs[0]);
            }
        }
        // Fallback to String if type parameter cannot be extracted
        return objectMapper.getTypeFactory().constructType(String.class);
    }
}
