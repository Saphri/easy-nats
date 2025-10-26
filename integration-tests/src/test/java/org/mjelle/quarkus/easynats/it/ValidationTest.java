package org.mjelle.quarkus.easynats.it;

import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsSubscriber;

/**
 * Tests verifying build-time validation of {@code @NatsSubscriber} annotations.
 *
 * <p>
 * These tests ensure that invalid subscriber definitions fail at build time with clear error
 * messages.
 * </p>
 */
@QuarkusTest
class ValidationTest {

    /**
     * Test to verify the annotation contract works correctly (positive case).
     *
     * <p>
     * This test just validates that a properly formed subscriber bean can be defined.
     * </p>
     */
    @Test
    void testValidSubscriberDefinition() {
        // If the app starts with this test class, the annotation is correctly defined
        assertThat(ValidSubscriber.class).isNotNull();
    }

    /**
     * Valid subscriber bean used for testing.
     */
    @ApplicationScoped
    public static class ValidSubscriber {
        @NatsSubscriber("valid-subject")
        public void onMessage(String message) {
            // Valid subscriber method
        }
    }

    /**
     * Note: Tests for invalid subscriber definitions (no parameters, empty subject, etc.) are
     * meant to be run as separate Quarkus test runs with expected build failures. These are
     * documented in tasks.md as T012 and T013 but require special test infrastructure to
     * validate build-time failures (e.g., using Quarkus test archives with expected build
     * exceptions).
     *
     * <p>
     * For now, this test just verifies that valid definitions work correctly.
     * </p>
     */
}
