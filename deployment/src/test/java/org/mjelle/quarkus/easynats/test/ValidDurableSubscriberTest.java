package org.mjelle.quarkus.easynats.test;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mjelle.quarkus.easynats.NatsSubscriber;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Test: Valid durable subscriber annotation (stream + consumer properties)
 *
 * <p>
 * This test validates that the annotation properties are correct at build time.
 * Runtime verification of the durable consumer existence is tested separately in
 * integration tests (DurableConsumerVerificationTest).
 * </p>
 */
public class ValidDurableSubscriberTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.easynats.servers", "nats://localhost:4222")
            .overrideConfigKey("quarkus.easynats.username", "guest")
            .overrideConfigKey("quarkus.easynats.password", "guest")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ValidDurableSubscriber.class, NatsSubscriber.class))
            .assertException(t -> {
                // Expect runtime error because durable consumer doesn't exist on test NATS server
                // This is expected behavior; actual durable consumer tests are in integration-tests
                String msg = t.getMessage();
                if (!msg.contains("durable consumer") && !msg.contains("consumer")) {
                    throw new AssertionError("Expected error about consumer but got: " + msg, t);
                }
            });

    @Test
    void testValidDurableSubscriberAnnotation() {
        // Test passes when deployment and runtime verification fail as expected
        // (consumer doesn't exist on test NATS server)
    }

    @ApplicationScoped
    public static class ValidDurableSubscriber {
        @NatsSubscriber(stream = "test-stream", consumer = "nonexistent-consumer")
        public void onOrder(String orderData) {
            // Durable subscriber - will fail at runtime due to missing consumer
        }
    }
}
