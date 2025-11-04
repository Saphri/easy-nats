package org.mjelle.quarkus.easynats.runtime.subscriber;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TypeValidator - Jackson type compatibility validation.
 *
 * <p>Tests cover: - Primitive type rejection - Array type rejection - Missing no-arg constructor
 * rejection - Valid Jackson-compatible types acceptance
 */
@DisplayName("TypeValidator - Type Validation")
class TypeValidatorTest {

  private TypeValidator validator;

  @BeforeEach
  void setUp() {
    validator = new TypeValidator();
  }

  // ============ Primitive Type Tests (T030) ============

  @Test
  @DisplayName(
      "T030: testPrimitiveIntRejected - should reject primitive int with wrapper suggestion")
  void testPrimitiveIntRejected() {
    // When
    TypeValidationResult result = validator.validate(int.class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.PRIMITIVE_TYPE);
    assertThat(result.getErrorMessage())
        .contains("Primitive type 'int' is not supported")
        .contains("Wrap it in a POJO")
        .contains("IntValue");
  }

  @Test
  @DisplayName(
      "T030: testPrimitiveLongRejected - should reject primitive long with wrapper suggestion")
  void testPrimitiveLongRejected() {
    // When
    TypeValidationResult result = validator.validate(long.class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.PRIMITIVE_TYPE);
    assertThat(result.getErrorMessage())
        .contains("Primitive type 'long' is not supported")
        .contains("Wrap it in a POJO")
        .contains("LongValue");
  }

  @Test
  @DisplayName(
      "T030: testPrimitiveDoubleRejected - should reject primitive double with wrapper suggestion")
  void testPrimitiveDoubleRejected() {
    // When
    TypeValidationResult result = validator.validate(double.class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.PRIMITIVE_TYPE);
    assertThat(result.getErrorMessage())
        .contains("Primitive type 'double' is not supported")
        .contains("Wrap it in a POJO")
        .contains("DoubleValue");
  }

  @Test
  @DisplayName("T030: testPrimitiveBooleanRejected - should reject primitive boolean")
  void testPrimitiveBooleanRejected() {
    // When
    TypeValidationResult result = validator.validate(boolean.class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.PRIMITIVE_TYPE);
    assertThat(result.getErrorMessage()).contains("Primitive type 'boolean' is not supported");
  }

  // ============ Array Type Tests (T031) ============

  @Test
  @DisplayName("T031: testArrayIntRejected - should reject int[] with wrapper suggestion")
  void testArrayIntRejected() {
    // When
    TypeValidationResult result = validator.validate(int[].class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.ARRAY_TYPE);
    assertThat(result.getErrorMessage())
        .contains("Array type")
        .contains("is not supported")
        .contains("Wrap it in a POJO");
  }

  @Test
  @DisplayName("T031: testArrayStringRejected - should reject String[] with wrapper suggestion")
  void testArrayStringRejected() {
    // When
    TypeValidationResult result = validator.validate(String[].class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.ARRAY_TYPE);
    assertThat(result.getErrorMessage())
        .contains("Array type")
        .contains("is not supported")
        .contains("Wrap it in a POJO");
  }

  @Test
  @DisplayName("T031: testArrayLongRejected - should reject long[] with wrapper suggestion")
  void testArrayLongRejected() {
    // When
    TypeValidationResult result = validator.validate(long[].class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.ARRAY_TYPE);
    assertThat(result.getErrorMessage()).contains("Array type").contains("is not supported");
  }

  // ============ Missing No-Arg Constructor Tests (T032) ============

  @Test
  @DisplayName("T032: testMissingNoArgCtorRejected - should reject type without no-arg constructor")
  void testMissingNoArgCtorRejected() {
    // When
    TypeValidationResult result = validator.validate(TypeWithoutNoArgCtor.class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.MISSING_NO_ARG_CTOR);
    assertThat(result.getErrorMessage())
        .contains("requires a no-arg constructor")
        .contains("Add a no-arg constructor");
  }

  @Test
  @DisplayName(
      "T032: testTypeWithRequiredFieldsRejected - should reject type with only required constructor")
  void testTypeWithRequiredFieldsRejected() {
    // When
    TypeValidationResult result = validator.validate(OrderWithoutNoArgCtor.class);

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.MISSING_NO_ARG_CTOR);
    assertThat(result.getErrorMessage()).contains("no-arg constructor");
  }

  // ============ Valid Type Tests ============

  @Test
  @DisplayName("should accept simple POJO with no-arg constructor")
  void testValidPojoAccepted() {
    // When
    TypeValidationResult result = validator.validate(ValidPojo.class);

    // Then
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorMessage()).isNull();
    assertThat(result.getErrorType()).isNull();
  }

  @Test
  @DisplayName("should accept String type")
  void testValidStringAccepted() {
    // When
    TypeValidationResult result = validator.validate(String.class);

    // Then
    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("should reject List interface (abstract, no implementation)")
  void testListInterfaceRejected() {
    // When - List is an interface and cannot be instantiated by Jackson
    TypeValidationResult result = validator.validate(List.class);

    // Then - List is rejected because it has no no-arg constructor (it's an interface)
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ValidationErrorType.MISSING_NO_ARG_CTOR);
    assertThat(result.getErrorMessage()).contains("no-arg constructor");
  }

  // ============ Test Helper Classes ============

  /** Valid POJO with no-arg constructor and fields. */
  static class ValidPojo {
    private String id;
    private int value;

    public ValidPojo() {
      // no-arg constructor required for Jackson
    }

    public ValidPojo(String id, int value) {
      this.id = id;
      this.value = value;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public int getValue() {
      return value;
    }

    public void setValue(int value) {
      this.value = value;
    }
  }

  /** Type WITHOUT no-arg constructor - only has parameterized constructor. */
  static class TypeWithoutNoArgCtor {
    private String value;

    // Only parameterized constructor, no no-arg constructor
    public TypeWithoutNoArgCtor(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /** Order type without no-arg constructor - represents a real-world case. */
  static class OrderWithoutNoArgCtor {
    private String orderId;
    private String customerId;

    // Only requires both fields, no no-arg constructor
    public OrderWithoutNoArgCtor(String orderId, String customerId) {
      this.orderId = orderId;
      this.customerId = customerId;
    }

    public String getOrderId() {
      return orderId;
    }

    public String getCustomerId() {
      return customerId;
    }
  }
}
