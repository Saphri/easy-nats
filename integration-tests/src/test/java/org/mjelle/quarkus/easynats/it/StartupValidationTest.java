package org.mjelle.quarkus.easynats.it;

import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsSubscriber;

/**
 * Tests verifying startup validation of subscriber configurations.
 *
 * <p>
 * These tests ensure that the application fails fast and with clear error messages if any
 * subscription configuration is invalid at startup.
 * </p>
 */
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
class StartupValidationTest {

    /**
     * Test T018: Verify application startup succeeds when subscriptions are valid (FR-009).
     *
     * <p>
     * If this test class is instantiated and the app starts, then startup validation passed.
     * </p>
     */
    @Test
    void testApplicationStartsWithValidSubscriptions() {
        // If we reach here, the app started successfully, meaning all subscriptions are valid
        assertThat(ValidStartupSubscriber.class).isNotNull();
    }

    /**
     * Valid subscriber bean for testing startup.
     */
    @ApplicationScoped
    public static class ValidStartupSubscriber {
        @NatsSubscriber(subject = "test.startup.message")
        public void onStartupMessage(String message) {
            // Valid subscriber
        }
    }

    /**
     * Note: Tests for startup failures (invalid consumer creation, connection failures, etc.)
     * require special test infrastructure to validate application startup failures. These are
     * documented in tasks.md as T018 but require test archives with expected startup exceptions.
     *
     * <p>
     * For now, this test just verifies that valid configurations allow the app to start.
     * </p>
     */
}
