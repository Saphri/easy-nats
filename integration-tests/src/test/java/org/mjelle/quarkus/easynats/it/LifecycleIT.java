package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for NatsConnection lifecycle management.
 *
 * <p>Inherits all tests from LifecycleTest and runs them in native image context.
 */
@QuarkusIntegrationTest
public class LifecycleIT extends LifecycleTest {
  // Inherits all test methods from LifecycleTest
  // Tests run in native image context via @QuarkusIntegrationTest
}
