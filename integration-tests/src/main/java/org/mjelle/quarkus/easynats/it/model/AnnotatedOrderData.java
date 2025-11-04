package org.mjelle.quarkus.easynats.it.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Order data model with Jackson annotations for testing annotation support.
 *
 * <p>This model demonstrates that the library passes annotated types to Jackson unchanged. Jackson
 * handles all annotation processing - the library just delegates to
 * ObjectMapper.writeValueAsBytes() and ObjectMapper.readValue().
 *
 * <p>Annotations used: - @JsonProperty("order_id"): Renames field 'id' to 'order_id' in JSON
 * - @JsonIgnore on internalId: Field is excluded from JSON serialization - Regular fields without
 * annotations: Normal JSON mapping
 */
public class AnnotatedOrderData implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Order ID field - mapped to "order_id" in JSON. When serialized, this field appears as
   * "order_id" in the JSON. When deserialized, the "order_id" JSON key populates this field.
   */
  @JsonProperty("order_id")
  private String id;

  /**
   * Customer ID - normal field, no annotation. Appears as "customerId" in JSON (standard Java
   * naming convention).
   */
  private String customerId;

  /**
   * Total price - normal field, no annotation. Appears as "totalPrice" in JSON (standard Java
   * naming convention).
   */
  private double totalPrice;

  /**
   * Internal ID - excluded from JSON. This field is NOT included when serializing to JSON. When
   * deserializing, if "internalId" appears in JSON, it's ignored. This field will have its default
   * value (null) after deserialization.
   */
  @JsonIgnore private String internalId;

  /** No-arg constructor required for Jackson deserialization. */
  public AnnotatedOrderData() {
    this.internalId = null;
  }

  /**
   * Convenience constructor for testing (excludes internalId).
   *
   * @param id the order ID
   * @param customerId the customer ID
   * @param totalPrice the total price
   */
  public AnnotatedOrderData(String id, String customerId, double totalPrice) {
    this.id = id;
    this.customerId = customerId;
    this.totalPrice = totalPrice;
    this.internalId = null; // Not set - would be @JsonIgnore'd anyway
  }

  /**
   * Full constructor for internal testing only.
   *
   * @param id the order ID
   * @param customerId the customer ID
   * @param totalPrice the total price
   * @param internalId the internal ID (will be ignored in JSON)
   */
  public AnnotatedOrderData(String id, String customerId, double totalPrice, String internalId) {
    this.id = id;
    this.customerId = customerId;
    this.totalPrice = totalPrice;
    this.internalId = internalId;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public double getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(double totalPrice) {
    this.totalPrice = totalPrice;
  }

  public String getInternalId() {
    return internalId;
  }

  public void setInternalId(String internalId) {
    this.internalId = internalId;
  }

  @Override
  public String toString() {
    return """
            AnnotatedOrderData{
                id='%s', customerId='%s', totalPrice=%f, internalId='%s'
            }"""
        .formatted(id, customerId, totalPrice, internalId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnnotatedOrderData that = (AnnotatedOrderData) o;

    if (Double.compare(that.totalPrice, totalPrice) != 0) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (customerId != null ? !customerId.equals(that.customerId) : that.customerId != null)
      return false;
    // Note: internalId is NOT compared because it's @JsonIgnore'd
    // After deserialization, internalId will always be null
    return true;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = id != null ? id.hashCode() : 0;
    result = 31 * result + (customerId != null ? customerId.hashCode() : 0);
    temp = Double.doubleToLongBits(totalPrice);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    // Note: internalId NOT included in hash
    return result;
  }
}
