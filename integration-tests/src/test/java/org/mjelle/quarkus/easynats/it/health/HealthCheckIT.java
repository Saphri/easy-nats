package org.mjelle.quarkus.easynats.it.health;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for NATS health check endpoints.
 *
 * <p>Reuses all test methods from HealthCheckTest but runs them in a native image context. This
 * validates that the health check implementation works correctly in both JVM and native modes.
 */
@QuarkusIntegrationTest
public class HealthCheckIT extends HealthCheckTest {
  // Inherits all test methods from HealthCheckTest
}
