package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for negative acknowledgment (NatsMessage.nak()).
 *
 * <p>
 * Extends NakTest to reuse all test methods in native image context, ensuring the
 * negative ack functionality works correctly when compiled to native code with GraalVM.
 * </p>
 */
@QuarkusIntegrationTest
public class NakIT extends NakTest {
    // Inherits all test methods from NakTest
}
