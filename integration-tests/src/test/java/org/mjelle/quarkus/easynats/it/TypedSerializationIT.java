package org.mjelle.quarkus.easynats.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Native image integration tests for typed serialization.
 *
 * <p>
 * Extends {@link TypedSerializationTest} to run all typed serialization tests
 * in a native image context. This validates that the extension works correctly
 * when compiled to native executable.
 * </p>
 */
@QuarkusIntegrationTest
public class TypedSerializationIT extends TypedSerializationTest {
    // Inherits all test methods from TypedSerializationTest
    // ✓ Works because base class has no @Inject fields
}
