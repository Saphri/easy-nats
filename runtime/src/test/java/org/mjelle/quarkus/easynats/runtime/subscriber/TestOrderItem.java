package org.mjelle.quarkus.easynats.runtime.subscriber;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Test POJO for nested deserialization testing.
 */
public class TestOrderItem {

    @JsonProperty("sku")
    private String sku;

    @JsonProperty("quantity")
    private int quantity;

    public TestOrderItem() {
        // No-arg constructor for Jackson
    }

    public TestOrderItem(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
