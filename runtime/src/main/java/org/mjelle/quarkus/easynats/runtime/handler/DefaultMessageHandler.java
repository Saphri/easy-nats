package org.mjelle.quarkus.easynats.runtime.handler;

import io.nats.client.Message;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

/**
 * Default implementation of {@link MessageHandler}.
 *
 * <p>
 * This handler invokes a subscriber method with the message payload and handles message
 * acknowledgment based on the outcome.
 * </p>
 */
public class DefaultMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(DefaultMessageHandler.class);

    private final SubscriberMetadata metadata;
    private final Object bean;
    private final Method method;

    /**
     * Creates a new message handler.
     *
     * @param metadata the subscriber metadata
     * @param bean the bean instance containing the subscriber method
     * @param method the subscriber method
     */
    public DefaultMessageHandler(SubscriberMetadata metadata, Object bean, Method method) {
        this.metadata = metadata;
        this.bean = bean;
        this.method = method;
    }

    @Override
    public void handle(Message message) {
        try {
            String payload = new String(message.getData());
            invokeSubscriberMethod(payload);
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
     * @param payload the message payload
     * @throws IllegalAccessException if the method is not accessible
     * @throws InvocationTargetException if the method throws an exception
     */
    private void invokeSubscriberMethod(String payload)
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
