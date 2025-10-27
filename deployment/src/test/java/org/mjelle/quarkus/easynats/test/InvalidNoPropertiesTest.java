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
 * Test: Invalid - no properties provided
 */
public class InvalidNoPropertiesTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(InvalidNoProperties.class, NatsSubscriber.class))
            .assertException(t -> {
                if (!(t instanceof DefinitionException)) {
                    throw new AssertionError("Expected DefinitionException but got: " + t.getClass().getName(), t);
                }
                String msg = t.getMessage();
                if (!msg.contains("must specify either") || !msg.contains("subject") ||
                    !msg.contains("stream") || !msg.contains("consumer")) {
                    throw new AssertionError("Expected error message about specifying subject or stream/consumer but got: " + msg, t);
                }
            });

    @Test
    void test() {
        // Test passes if deployment fails with expected error
    }

    @ApplicationScoped
    public static class InvalidNoProperties {
        @NatsSubscriber()
        public void onMessage(String message) {
            // Invalid: no properties specified
        }
    }
}
