package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for NatsConnection try-with-resources support.
 * <p>
 * Inherits all tests from TryWithResourcesTest and runs them in native image context.
 * </p>
 */
@QuarkusIntegrationTest
public class TryWithResourcesIT extends TryWithResourcesTest {
    // Inherits all test methods from TryWithResourcesTest
    // Tests run in native image context via @QuarkusIntegrationTest
}
