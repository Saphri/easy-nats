package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native integration test for CloudEvent handling. Extends CloudEventTest to reuse all test
 * methods.
 *
 * <p>Runs against real NATS broker via docker-compose in full native integration mode. All test
 * methods from CloudEventTest are inherited and validated in this integration context.
 */
@QuarkusIntegrationTest
public class CloudEventIT extends CloudEventTest {
  // Inherits all test methods from CloudEventTest
}
