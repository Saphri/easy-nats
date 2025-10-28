package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for explicit acknowledgment (NatsMessage.ack()).
 *
 * <p>
 * Extends AckTest to reuse all test methods in native image context, ensuring the
 * explicit ack functionality works correctly when compiled to native code with GraalVM.
 * </p>
 */
@QuarkusIntegrationTest
public class AckIT extends AckTest {
    // Inherits all test methods from AckTest
}
