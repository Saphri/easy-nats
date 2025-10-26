package org.mjelle.quarkus.easynats.it;

/**
 * Test domain object for demonstrating typed publishing.
 * Simple POJO with fields that match integration test expectations.
 */
public class TestOrder {
    public String orderId;
    public int amount;

    public TestOrder() {
    }

    public TestOrder(String orderId, int amount) {
        this.orderId = orderId;
        this.amount = amount;
    }
}
