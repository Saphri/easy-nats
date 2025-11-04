package org.mjelle.quarkus.easynats.runtime.subscriber;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Test POJO with nested objects for deserialization testing. */
public class TestOrder {

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("customerId")
  private String customerId;

  @JsonProperty("items")
  private List<TestOrderItem> items;

  public TestOrder() {
    // No-arg constructor for Jackson
  }

  public TestOrder(String orderId, String customerId, List<TestOrderItem> items) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.items = items;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public List<TestOrderItem> getItems() {
    return items;
  }

  public void setItems(List<TestOrderItem> items) {
    this.items = items;
  }
}
