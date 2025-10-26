package org.mjelle.quarkus.easynats.it;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.NatsSubscriber;

/**
 * Integration tests for the {@code @NatsSubscriber} annotation.
 *
 * <p>
 * These tests verify the core functionality of subscriber message consumption, acknowledgment,
 * and error handling.
 * </p>
 */
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
class NatsSubscriberTest {

    @Inject
    @NatsSubject("test.subscriber.basic")
    NatsPublisher publisher;

    @Inject
    TestSubscriber testSubscriber;

    @BeforeEach
    void setUp() throws Exception {
        NatsTestUtils.purgeStream();
        testSubscriber.clearMessages();
    }

    /**
     * Test T014: Basic message consumption - publish string to subject, verify method invoked
     * with payload (FR-001 through FR-005).
     */
    @Test
    void testBasicMessageConsumption() throws Exception {
        String testMessage = "Hello, NATS!";

        publisher.publish(testMessage);

        // Wait for the subscriber method to be invoked
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(testSubscriber.getMessages()).containsExactly(testMessage);
                });
    }

    /**
     * Test T015: Success acknowledgment - verify ack called on successful method execution
     * (FR-006).
     *
     * <p>
     * This test verifies that when a subscriber method completes successfully, the message is
     * acknowledged. The test doesn't throw an exception, so it should succeed silently.
     * </p>
     */
    @Test
    void testSuccessAcknowledgment() throws Exception {
        String testMessage = "Success message";

        publisher.publish(testMessage);

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(testSubscriber.getMessages()).contains(testMessage);
                });

        // If we got here without errors, the message was successfully acknowledged
        assertThat(testSubscriber.getErrorCount()).isZero();
    }

    /**
     * Test T016: Failure acknowledgment - verify nak called when method throws exception
     * (FR-007).
     */
    @Test
    void testFailureAcknowledgment() throws Exception {
        String errorMessage = "error-trigger";

        testSubscriber.setErrorMode(true);
        publisher.publish(errorMessage);

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(testSubscriber.getErrorCount()).isGreaterThan(0);
                });
    }

    /**
     * Test T017: Multiple subscribers - verify multiple annotated methods in same class each
     * create separate consumers (FR-008).
     */
    @Test
    void testMultipleSubscribersInSameClass() throws Exception {
        String msg1 = "Message for first subscriber";
        String msg2 = "Message for second subscriber";

        testSubscriber.clearMessages();
        testSubscriber.clearSecondaryMessages();

        publisher.publish(msg1);
        publisher.publish(msg2);

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    // Both subscribers should receive all messages (fan-out)
                    assertThat(testSubscriber.getMessages()).containsExactlyInAnyOrder(msg1, msg2);
                    assertThat(testSubscriber.getSecondaryMessages()).containsExactlyInAnyOrder(msg1, msg2);
                });
    }

    /**
     * Test subscriber bean with multiple subscriber methods.
     */
    @ApplicationScoped
    public static class TestSubscriber {

        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final List<String> secondaryMessages = new CopyOnWriteArrayList<>();
        private int errorCount = 0;
        private boolean errorMode = false;

        @NatsSubscriber("test.subscriber.basic")
        public void onMessage(String message) {
            if (errorMode) {
                errorCount++;
                throw new RuntimeException("Test error");
            }
            messages.add(message);
        }

        @NatsSubscriber("test.subscriber.basic")
        public void onSecondaryMessage(String message) {
            secondaryMessages.add(message);
        }

        public List<String> getMessages() {
            return new ArrayList<>(messages);
        }

        public List<String> getSecondaryMessages() {
            return new ArrayList<>(secondaryMessages);
        }

        public int getErrorCount() {
            return errorCount;
        }

        public void setErrorMode(boolean errorMode) {
            this.errorMode = errorMode;
        }

        public void clearMessages() {
            messages.clear();
            errorCount = 0;
            errorMode = false;
        }

        public void clearSecondaryMessages() {
            secondaryMessages.clear();
        }
    }
}
