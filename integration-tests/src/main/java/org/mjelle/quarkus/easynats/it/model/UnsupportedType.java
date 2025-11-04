package org.mjelle.quarkus.easynats.it.model;

/**
 * Placeholder model for testing unsupported type validation.
 *
 * <p>This model is referenced in test code to verify that the library properly validates and
 * rejects types that cannot be serialized/deserialized.
 *
 * <p>Note: In practice, unsupported types (primitives, arrays) cannot be used directly as generic
 * type parameters in Java, so this class serves as documentation of what the validation should
 * prevent.
 *
 * <p>Jackson can actually deserialize primitive wrapper types (Integer, Long, etc.), but the
 * library validates against unsupported patterns before delegating to Jackson.
 */
public class UnsupportedType {
  private String description;

  public UnsupportedType() {
    this.description = "unsupported";
  }

  public UnsupportedType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
