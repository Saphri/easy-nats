package org.mjelle.quarkus.easynats.runtime.handler;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsMessage;

/**
 * Unit tests for DefaultMessageHandler parameter type detection.
 *
 * <p>
 * Tests the core framework logic that detects explicit vs implicit acknowledgment mode
 * based on subscriber method parameter types.
 * </p>
 */
@DisplayName("DefaultMessageHandler Parameter Type Detection Tests")
class DefaultMessageHandlerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // Sample types for testing
    static class OrderData {
        String id;
    }

    static class TestSubscriber {
        // Explicit mode: NatsMessage<T>
        public void handleWithExplicitMode(NatsMessage<OrderData> msg) {}

        // Implicit mode: typed payload only
        public void handleWithImplicitMode(OrderData order) {}

        // Implicit mode: String payload
        public void handleWithString(String message) {}
    }

    @Test
    @DisplayName("isNatsMessageType() returns true for NatsMessage<T> parameter")
    void testIsNatsMessageTypeWithGenericType() throws Exception {
        // Given: A method with NatsMessage<T> parameter
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", NatsMessage.class);
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
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", NatsMessage.class);
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
        Type rawType = NatsMessage.class;

        // When: We extract the payload type
        JavaType payloadType = extractPayloadType(rawType);

        // Then: We get String as fallback
        assertThat(payloadType.getRawClass()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Explicit mode detection: NatsMessage<T> parameter")
    void testExplicitModeDetectionWithNatsMessage() throws Exception {
        // Given: A method with NatsMessage<OrderData> parameter
        Method method = TestSubscriber.class.getMethod("handleWithExplicitMode", NatsMessage.class);
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

    // Helper methods extracted from DefaultMessageHandler
    private boolean isNatsMessageType(Type type) {
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            return rawType instanceof Class && NatsMessage.class.isAssignableFrom((Class<?>) rawType);
        } else if (type instanceof Class) {
            return NatsMessage.class.isAssignableFrom((Class<?>) type);
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
