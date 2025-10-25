package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Integration test for NatsPublisher.
 * Extends BasicPublisherTest to run the same test methods in full integration mode
 * against a real NATS broker via docker-compose.
 */
@QuarkusIntegrationTest
public class BasicPublisherIT extends BasicPublisherTest {
}
