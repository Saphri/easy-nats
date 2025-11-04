package org.mjelle.quarkus.easynats.it.model;

/**
 * Test model without a no-arg constructor. Used to test that types without no-arg constructors are
 * rejected by TypeValidator. Jackson cannot deserialize this type because it requires the name and
 * value parameters.
 */
public class InvalidType {
  private String name;
  private int value;

  /**
   * Only constructor requires both name and value. There is NO no-arg constructor, which makes this
   * type invalid for Jackson deserialization.
   *
   * @param name the name field
   * @param value the value field
   */
  public InvalidType(String name, int value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public int getValue() {
    return value;
  }
}
