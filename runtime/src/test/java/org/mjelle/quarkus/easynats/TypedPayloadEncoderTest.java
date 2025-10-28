package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TypedPayloadEncoder - Jackson-only serialization.
 *
 * Tests cover:
 * - Simple POJO serialization
 * - Record type serialization
 * - Generic type serialization
 * - Error handling and exceptions
 */
@DisplayName("TypedPayloadEncoder Tests (Jackson-Only)")
class TypedPayloadEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("encodeWithJackson serializes simple POJO to JSON bytes")
    void testEncodeWithJacksonForSimplePojo() throws Exception {
        // Given
        TestPojo pojo = new TestPojo("value1", 42);

        // When
        byte[] result = TypedPayloadEncoder.encodeWithJackson(pojo, objectMapper);

        // Then
        assertThat(result).isNotEmpty();
        TestPojo deserialized = objectMapper.readValue(result, TestPojo.class);
        assertThat(deserialized.field1).isEqualTo("value1");
        assertThat(deserialized.field2).isEqualTo(42);
    }

    @Test
    @DisplayName("encodeWithJackson serializes record type to JSON bytes")
    void testEncodeWithJacksonForRecord() throws Exception {
        // Given - Use a simple record-like test class
        OrderTestRecord testRecord = new OrderTestRecord("ORD-001", "CUST-001", 150.00);

        // When
        byte[] result = TypedPayloadEncoder.encodeWithJackson(testRecord, objectMapper);

        // Then
        assertThat(result).isNotEmpty();
        OrderTestRecord deserialized = objectMapper.readValue(result, OrderTestRecord.class);
        assertThat(deserialized.orderId).isEqualTo("ORD-001");
        assertThat(deserialized.customerId).isEqualTo("CUST-001");
        assertThat(deserialized.totalPrice).isEqualTo(150.00);
    }

    @Test
    @DisplayName("encodeWithJackson serializes complex nested POJO")
    void testEncodeWithJacksonForComplexPojo() throws Exception {
        // Given
        ComplexPojo complex = new ComplexPojo(
                "nested-001",
                new TestPojo("inner", 99),
                new BigDecimal("123.45"));

        // When
        byte[] result = TypedPayloadEncoder.encodeWithJackson(complex, objectMapper);

        // Then
        assertThat(result).isNotEmpty();
        ComplexPojo deserialized = objectMapper.readValue(result, ComplexPojo.class);
        assertThat(deserialized.id).isEqualTo("nested-001");
        assertThat(deserialized.innerPojo.field1).isEqualTo("inner");
        assertThat(deserialized.innerPojo.field2).isEqualTo(99);
        assertThat(deserialized.amount).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    @DisplayName("encodeWithJackson throws SerializationException for null payload")
    void testEncodeWithJacksonThrowsForNullPayload() {
        // Given - null payload
        Object nullPayload = null;

        // Then
        assertThatThrownBy(() -> TypedPayloadEncoder.encodeWithJackson(nullPayload, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be null");
    }

    @Test
    @DisplayName("encodeWithJackson throws SerializationException for non-serializable object")
    void testEncodeWithJacksonThrowsSerializationExceptionForInvalidObject() {
        // Given - a class that Jackson cannot serialize (infinite recursion)
        TestPojoWithCycle pojo = new TestPojoWithCycle("test", 1);
        pojo.cyclic = pojo;  // Create cycle

        // Then
        assertThatThrownBy(() -> TypedPayloadEncoder.encodeWithJackson(pojo, objectMapper))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to serialize TestPojoWithCycle");
    }

    @Test
    @DisplayName("encodeWithJackson handles String payload")
    void testEncodeWithJacksonForString() throws Exception {
        // Given
        String text = "hello world";

        // When
        byte[] result = TypedPayloadEncoder.encodeWithJackson(text, objectMapper);

        // Then
        assertThat(result).isNotEmpty();
        String deserialized = objectMapper.readValue(result, String.class);
        assertThat(deserialized).isEqualTo(text);
    }

    @Test
    @DisplayName("encodeWithJackson handles numeric types")
    void testEncodeWithJacksonForNumeric() throws Exception {
        // Given
        Integer number = 42;

        // When
        byte[] result = TypedPayloadEncoder.encodeWithJackson(number, objectMapper);

        // Then
        assertThat(result).isNotEmpty();
        Integer deserialized = objectMapper.readValue(result, Integer.class);
        assertThat(deserialized).isEqualTo(42);
    }

    // Test helper classes

    static class TestPojo {
        public String field1;
        public int field2;

        public TestPojo() {
            // Default constructor for Jackson
        }

        public TestPojo(String field1, int field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    static class OrderTestRecord {
        public String orderId;
        public String customerId;
        public double totalPrice;

        public OrderTestRecord() {
            // Default constructor for Jackson
        }

        public OrderTestRecord(String orderId, String customerId, double totalPrice) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.totalPrice = totalPrice;
        }
    }

    static class ComplexPojo {
        public String id;
        public TestPojo innerPojo;
        public BigDecimal amount;

        public ComplexPojo() {
            // Default constructor for Jackson
        }

        public ComplexPojo(String id, TestPojo innerPojo, BigDecimal amount) {
            this.id = id;
            this.innerPojo = innerPojo;
            this.amount = amount;
        }
    }

    static class TestPojoWithCycle {
        public String name;
        public int value;
        public TestPojoWithCycle cyclic;  // Can be set to create cycle

        public TestPojoWithCycle() {
            // Default constructor for Jackson
        }

        public TestPojoWithCycle(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
