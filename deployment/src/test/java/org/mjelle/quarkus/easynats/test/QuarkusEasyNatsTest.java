package org.mjelle.quarkus.easynats.test;

/**
 * MOVED TO: integration-tests/src/test/java/org/mjelle/quarkus/easynats/it/QuarkusEasyNatsTest.java
 *
 * This test was moved from the deployment module to the integration-tests module because:
 * 1. QuarkusUnitTest is for build-time processor testing, not runtime functionality
 * 2. Runtime tests require a real NATS connection which cannot be provided in QuarkusUnitTest
 * 3. Integration tests have Dev Services which automatically provides NATS
 * 4. The test now validates actual extension functionality with a real NATS instance
 * 5. Follows the project's integration test patterns (Test + IT variants)
 *
 * The original template test is now an actual integration test that verifies
 * the extension loads correctly and can communicate with NATS via Dev Services.
 */
public class QuarkusEasyNatsTest {
}
