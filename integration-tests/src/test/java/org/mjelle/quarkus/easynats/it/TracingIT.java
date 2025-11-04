package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for distributed tracing.
 *
 * <p>Extends {@link TracingTest} to validate that the tracing functionality works correctly in both
 * JVM and native image contexts.
 */
@QuarkusIntegrationTest
public class TracingIT extends TracingTest {
  // Inherits all test methods from TracingTest
}
