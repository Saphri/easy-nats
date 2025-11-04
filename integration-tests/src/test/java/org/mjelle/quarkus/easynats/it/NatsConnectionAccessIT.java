package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for NatsConnection access API.
 *
 * <p>Inherits all tests from NatsConnectionAccessTest and runs them in native image context.
 */
@QuarkusIntegrationTest
public class NatsConnectionAccessIT extends NatsConnectionAccessTest {
  // Inherits all test methods from NatsConnectionAccessTest
  // Tests run in native image context via @QuarkusIntegrationTest
}
