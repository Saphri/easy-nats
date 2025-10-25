package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration test for NatsPublisher.
 * Extends BasicPublisherTest to reuse all test methods.
 *
 * Runs against real NATS broker via docker-compose in full integration mode.
 * All test methods from BasicPublisherTest are inherited and validated in this
 * integration context.
 */
@QuarkusIntegrationTest
public class BasicPublisherIT extends BasicPublisherTest {
    // Inherits all test methods from BasicPublisherTest
}
