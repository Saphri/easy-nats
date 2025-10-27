package org.mjelle.quarkus.easynats.runtime;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ErrorMessageFormatter - Error message generation and formatting.
 *
 * Tests cover:
 * - Primitive type error messages include wrapper pattern examples
 * - Array type error messages include wrapper pattern examples
 * - Missing no-arg constructor error messages provide clear guidance
 * - All error messages are properly formatted and actionable
 */
@DisplayName("ErrorMessageFormatter - Error Message Generation")
class ErrorMessageFormatterTest {

    @Test
    @DisplayName("T040: formatPrimitiveTypeError includes wrapping example")
    void testPrimitiveTypeErrorIncludesWrappingExample() {
        // When
        String errorMsg = ErrorMessageFormatter.formatPrimitiveTypeError(int.class);

        // Then
        assertThat(errorMsg)
            .contains("Primitive type 'int' is not supported")
            .contains("Wrap it in a POJO")
            .contains("public class IntValue {")
            .contains("public int value;")
            .contains("public IntValue() {}")
            .contains("public IntValue(int value) { this.value = value; }");
    }

    @Test
    @DisplayName("T040: formatArrayTypeError includes wrapping example")
    void testArrayTypeErrorIncludesWrappingExample() {
        // When
        String errorMsg = ErrorMessageFormatter.formatArrayTypeError(int[].class);

        // Then
        assertThat(errorMsg)
            .contains("Array type")
            .contains("is not supported")
            .contains("Wrap it in a POJO")
            .contains("public class IntList {")
            .contains("public int[] items;")
            .contains("public IntList() {}")
            .contains("public IntList(int[] items) { this.items = items; }");
    }

    @Test
    @DisplayName("T040: formatMissingNoArgCtorError provides clear guidance")
    void testMissingNoArgCtorErrorProvidesClearGuidance() {
        // When
        String errorMsg = ErrorMessageFormatter.formatMissingNoArgCtorError(MyClass.class);

        // Then
        assertThat(errorMsg)
            .contains("requires a no-arg constructor")
            .contains("Add a no-arg constructor")
            .contains("public MyClass() {}")
            .contains("@JsonDeserialize");
    }

    @Test
    @DisplayName("Error messages are complete and not truncated")
    void testErrorMessagesAreNotTruncated() {
        // When - Generate error for long type name
        String errorMsg = ErrorMessageFormatter.formatPrimitiveTypeError(long.class);

        // Then - Message should be complete with all guidance
        assertThat(errorMsg)
            .isNotEmpty()
            .contains("Wrap it in a POJO")
            .contains("public class")
            .doesNotEndWith("...");
    }

    @Test
    @DisplayName("Deserialization error message includes type and payload context")
    void testDeserializationErrorIncludesContext() {
        // Given
        String typeName = "OrderData";
        String rawPayload = "{\"orderId\": \"ORD-001\", \"invalid\": \"data\"}";
        String rootCause = "Unrecognized field 'invalid'";

        // When
        String errorMsg = ErrorMessageFormatter.formatDeserializationError(
            typeName, rawPayload, rootCause);

        // Then
        assertThat(errorMsg)
            .contains("Failed to deserialize to type 'OrderData'")
            .contains(rootCause)
            .contains(rawPayload)
            .contains("no-arg constructor")
            .contains("@JsonProperty")
            .contains("@JsonDeserialize");
    }

    @Test
    @DisplayName("Serialization error message includes type and suggestions")
    void testSerializationErrorIncludesSuggestions() {
        // Given
        String typeName = "CyclicType";
        String rootCause = "Infinite recursion detected";

        // When
        String errorMsg = ErrorMessageFormatter.formatSerializationError(typeName, rootCause);

        // Then
        assertThat(errorMsg)
            .contains("Failed to serialize type 'CyclicType'")
            .contains(rootCause)
            .contains("@JsonIgnore")
            .contains("@JsonSerialize");
    }

    // Test helper class
    static class MyClass {
        private String required;

        public MyClass(String required) {
            this.required = required;
        }

        public String getRequired() {
            return required;
        }
    }
}
