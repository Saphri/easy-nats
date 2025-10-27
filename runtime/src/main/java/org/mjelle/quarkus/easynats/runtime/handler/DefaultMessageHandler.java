package org.mjelle.quarkus.easynats.runtime.handler;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.subscriber.CloudEventException;
import org.mjelle.quarkus.easynats.runtime.subscriber.CloudEventUnwrapper;
import org.mjelle.quarkus.easynats.runtime.subscriber.DeserializationException;
import org.mjelle.quarkus.easynats.runtime.subscriber.MessageDeserializer;

/**
 * Default implementation of {@link MessageHandler}.
 *
 * <p>
 * This handler invokes a subscriber method with the message payload and handles message
 * acknowledgment based on the outcome.
 * </p>
 *
 * <p>
 * Supports both String payloads (004-nats-subscriber-mvp) and typed objects with
 * CloudEvent unwrapping and Jackson deserialization (006-typed-subscriber).
 * </p>
 */
public class DefaultMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(DefaultMessageHandler.class);
    private static final String STRING_TYPE_NAME = "java.lang.String";

    private final SubscriberMetadata metadata;
    private final Object bean;
    private final Method method;
    private final ObjectMapper objectMapper;
    private final JavaType parameterType;
    private final boolean isStringType;

    /**
     * Creates a new message handler.
     *
     * @param metadata the subscriber metadata
     * @param bean the bean instance containing the subscriber method
     * @param method the subscriber method
     * @param objectMapper the Jackson ObjectMapper for typed deserialization
     */
    public DefaultMessageHandler(SubscriberMetadata metadata, Object bean, Method method,
            ObjectMapper objectMapper) {
        this.metadata = metadata;
        this.bean = bean;
        this.method = method;
        this.objectMapper = objectMapper;

        // Determine parameter type from the Method's generic parameter type
        // This avoids needing to Class.forName() user types which aren't available at runtime
        Type genericParamType = method.getGenericParameterTypes()[0];
        this.parameterType = objectMapper.getTypeFactory().constructType(genericParamType);

        // Check if it's a String type
        this.isStringType = STRING_TYPE_NAME.equals(parameterType.getRawClass().getName());
    }

    @Override
    public void handle(Message message) {
        try {
            Object payload;

            if (isStringType) {
                // 004-nats-subscriber-mvp: String-only payload (backward compatibility)
                payload = new String(message.getData(), StandardCharsets.UTF_8);
            } else {
                // 006-typed-subscriber: CloudEvent unwrap + typed deserialization
                try {
                    // Step 1: Unwrap CloudEvent (binary-mode)
                    byte[] eventData = CloudEventUnwrapper.unwrapData(message);

                    // Step 2: Deserialize to typed object using JavaType
                    // This handles complex types, generics, and user-defined classes
                    try {
                        payload = objectMapper.readValue(eventData, parameterType);
                    } catch (Exception e) {
                        throw new DeserializationException(
                                "Failed to deserialize to type " + parameterType.getTypeName(), e);
                    }
                } catch (CloudEventException e) {
                    LOGGER.errorf(
                            "CloudEvent validation failed for subject=%s, method=%s, cause=%s",
                            metadata.subject(), metadata.methodName(), e.getMessage());
                    nakMessage(message);
                    return;
                } catch (DeserializationException e) {
                    LOGGER.errorf(
                            "Message deserialization failed for subject=%s, method=%s, type=%s, cause=%s",
                            metadata.subject(), metadata.methodName(), parameterType.getTypeName(),
                            e.getMessage());
                    nakMessage(message);
                    return;
                }
            }

            // Step 3: Invoke method with payload
            invokeSubscriberMethod(payload);

            // Step 4: Ack on success
            message.ack();
        } catch (InvocationTargetException e) {
            // The subscriber method threw an exception
            LOGGER.errorf(
                    e.getCause(),
                    "Error processing message for subscriber: subject=%s, method=%s",
                    metadata.subject(),
                    metadata.methodName());
            nakMessage(message);
        } catch (Exception e) {
            // Other errors (reflection, etc.)
            LOGGER.errorf(
                    e,
                    "Error processing message for subscriber: subject=%s, method=%s",
                    metadata.subject(),
                    metadata.methodName());
            nakMessage(message);
        }
    }

    /**
     * Invokes the subscriber method with the given payload.
     *
     * @param payload the message payload (String or typed object)
     * @throws IllegalAccessException if the method is not accessible
     * @throws InvocationTargetException if the method throws an exception
     */
    private void invokeSubscriberMethod(Object payload)
            throws IllegalAccessException, InvocationTargetException {
        method.invoke(bean, payload);
    }

    /**
     * Negatively acknowledges a message.
     *
     * @param message the message to nak
     */
    private void nakMessage(Message message) {
        try {
            message.nak();
        } catch (Exception nakError) {
            LOGGER.errorf(
                    nakError,
                    "Failed to nak message for subject=%s, method=%s",
                    metadata.subject(),
                    metadata.methodName());
        }
    }
}
