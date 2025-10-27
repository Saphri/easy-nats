package org.mjelle.quarkus.easynats.test;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import jakarta.enterprise.inject.spi.DefinitionException;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Test: Invalid - only consumer without stream
 */
public class InvalidOnlyConsumerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(InvalidOnlyConsumer.class, NatsSubscriber.class))
            .assertException(t -> {
                if (!(t instanceof DefinitionException)) {
                    throw new AssertionError("Expected DefinitionException but got: " + t.getClass().getName(), t);
                }
                if (!t.getMessage().contains("missing 'stream'")) {
                    throw new AssertionError("Expected \"missing 'stream'\" in error message but got: " + t.getMessage(), t);
                }
            });

    @Test
    void test() {
        // Test passes if deployment fails with expected error
    }

    @ApplicationScoped
    public static class InvalidOnlyConsumer {
        @NatsSubscriber(consumer = "processor")
        public void onMessage(String message) {
            // Invalid: consumer without stream
        }
    }
}
