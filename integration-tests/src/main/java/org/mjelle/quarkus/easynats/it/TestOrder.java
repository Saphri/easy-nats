package org.mjelle.quarkus.easynats.it;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Test domain object for demonstrating typed publishing.
 * Record with fields that match integration test expectations.
 */
@RegisterForReflection
public record TestOrder(String orderId, int amount) {
}
