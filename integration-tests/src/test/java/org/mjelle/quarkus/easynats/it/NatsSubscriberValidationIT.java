package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for @NatsSubscriber annotation validation.
 *
 * Extends the JVM tests to validate they work in native image mode.
 */
@QuarkusIntegrationTest
public class NatsSubscriberValidationIT extends NatsSubscriberValidationTest {
}
