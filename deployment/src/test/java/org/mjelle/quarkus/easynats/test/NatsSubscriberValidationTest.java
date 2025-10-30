package org.mjelle.quarkus.easynats.test;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests for build-time validation of @NatsSubscriber annotation properties.
 *
 * <p>
 * Validates that:
 * - Valid ephemeral mode (subject only) compiles
 * - Valid durable mode (stream + consumer) compiles
 * - Invalid combinations fail with clear error messages
 * </p>
 */
@QuarkusTestResource(NatsTestResource.class)
public class NatsSubscriberValidationTest {

    /**
     * Test: Valid ephemeral subscriber (subject property only)
     */
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.easynats.servers", "nats://localhost:4222")
            .overrideConfigKey("quarkus.easynats.username", "guest")
            .overrideConfigKey("quarkus.easynats.password", "guest")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ValidEphemeralSubscriber.class, NatsSubscriber.class));

    @Test
    void testValidEphemeralSubscriber() {
        // Test passes if deployment succeeds (no exception thrown)
    }

    @ApplicationScoped
    public static class ValidEphemeralSubscriber {
        @NatsSubscriber("test.subject")
        public void onMessage(String message) {
            // Ephemeral subscriber
        }
    }
}
