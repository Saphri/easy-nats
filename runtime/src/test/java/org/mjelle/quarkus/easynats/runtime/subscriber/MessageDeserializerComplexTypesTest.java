package org.mjelle.quarkus.easynats.runtime.subscriber;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MessageDeserializer - Jackson-only deserialization.
 *
 * Tests cover:
 * - Simple POJO deserialization
 * - Nested POJO deserialization
 * - Generic type deserialization (List<T>, Map<K,V>)
 * - Error handling and exceptions
 */
@DisplayName("MessageDeserializer - Jackson-Only Deserialization")
class MessageDeserializerComplexTypesTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("should deserialize POJO with all fields populated")
    void testPojoDeserialization() throws Exception {
        // Given
        String json = "{\"userId\":\"USER-001\",\"name\":\"John Doe\",\"email\":\"john@example.com\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType userType = objectMapper.getTypeFactory().constructType(TestUser.class);

        // When
        TestUser result = MessageDeserializer.deserialize(data, userType, objectMapper);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("USER-001");
        assertThat(result.getName()).isEqualTo("John Doe");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("should deserialize nested POJO (Order containing OrderItems)")
    void testNestedPojoDeserialization() throws Exception {
        // Given
        String json = "{\"orderId\":\"ORD-123\",\"customerId\":\"CUST-456\",\"items\":[{\"sku\":\"ITEM-001\",\"quantity\":2},{\"sku\":\"ITEM-002\",\"quantity\":1}]}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType orderType = objectMapper.getTypeFactory().constructType(TestOrder.class);

        // When
        TestOrder result = MessageDeserializer.deserialize(data, orderType, objectMapper);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-123");
        assertThat(result.getCustomerId()).isEqualTo("CUST-456");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getSku()).isEqualTo("ITEM-001");
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getItems().get(1).getSku()).isEqualTo("ITEM-002");
        assertThat(result.getItems().get(1).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("should deserialize List<T> generic type")
    void testGenericListDeserialization() throws Exception {
        // Given
        String json = "[{\"userId\":\"USER-001\",\"name\":\"John\",\"email\":\"john@example.com\"},{\"userId\":\"USER-002\",\"name\":\"Jane\",\"email\":\"jane@example.com\"}]";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType listUserType = objectMapper.getTypeFactory()
                .constructParametricType(List.class, TestUser.class);

        // When
        List<TestUser> result = MessageDeserializer.deserialize(data, listUserType, objectMapper);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo("USER-001");
        assertThat(result.get(0).getName()).isEqualTo("John");
        assertThat(result.get(1).getUserId()).isEqualTo("USER-002");
        assertThat(result.get(1).getName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("should throw DeserializationException when JSON doesn't match POJO type")
    void testJsonTypeRejection() {
        // Given
        String json = "{\"wrongField\":\"value\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType userType = objectMapper.getTypeFactory().constructType(TestUser.class);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, userType, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("Failed to deserialize");
    }

    @Test
    @DisplayName("should deserialize POJO even with missing optional field (Jackson allows it)")
    void testMissingRequiredField() throws Exception {
        // Given - missing 'userId' field (Jackson allows this and sets to null)
        String json = "{\"name\":\"John\",\"email\":\"john@example.com\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType userType = objectMapper.getTypeFactory().constructType(TestUser.class);

        // When
        TestUser result = MessageDeserializer.deserialize(data, userType, objectMapper);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isNull();
        assertThat(result.getName()).isEqualTo("John");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("should throw DeserializationException for malformed JSON")
    void testMalformedJson() {
        // Given
        byte[] data = "{invalid json}".getBytes(StandardCharsets.UTF_8);
        JavaType userType = objectMapper.getTypeFactory().constructType(TestUser.class);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, userType, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("Failed to deserialize");
    }

    @Test
    @DisplayName("should use custom ObjectMapper when provided")
    void testCustomObjectMapperUsage() throws Exception {
        // Given
        ObjectMapper customMapper = new ObjectMapper();
        String json = "{\"userId\":\"USER-001\",\"name\":\"John\",\"email\":\"john@example.com\"}";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType userType = customMapper.getTypeFactory().constructType(TestUser.class);

        // When
        TestUser result = MessageDeserializer.deserialize(data, userType, customMapper);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("USER-001");
    }

    @Test
    @DisplayName("should handle complex nested list deserialization")
    void testComplexNestedListDeserialization() throws Exception {
        // Given
        String json = "[{\"orderId\":\"ORD-001\",\"customerId\":\"CUST-001\",\"items\":[{\"sku\":\"SKU-001\",\"quantity\":1}]},{\"orderId\":\"ORD-002\",\"customerId\":\"CUST-002\",\"items\":[{\"sku\":\"SKU-002\",\"quantity\":2}]}]";
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        JavaType listOrderType = objectMapper.getTypeFactory()
                .constructParametricType(List.class, TestOrder.class);

        // When
        List<TestOrder> result = MessageDeserializer.deserialize(data, listOrderType, objectMapper);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrderId()).isEqualTo("ORD-001");
        assertThat(result.get(0).getItems()).hasSize(1);
        assertThat(result.get(1).getOrderId()).isEqualTo("ORD-002");
        assertThat(result.get(1).getItems()).hasSize(1);
    }

    @Test
    @DisplayName("should throw DeserializationException for empty payload")
    void testEmptyPayload() {
        // Given
        byte[] data = "".getBytes(StandardCharsets.UTF_8);
        JavaType userType = objectMapper.getTypeFactory().constructType(TestUser.class);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, userType, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("Data cannot be null or empty");
    }

    // Test helper classes

    static class TestUser {
        private String userId;
        private String name;
        private String email;

        public TestUser() {
            // Default constructor for Jackson
        }

        public TestUser(String userId, String name, String email) {
            this.userId = userId;
            this.name = name;
            this.email = email;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    static class TestOrder {
        private String orderId;
        private String customerId;
        private List<OrderItem> items;

        public TestOrder() {
            // Default constructor for Jackson
        }

        public TestOrder(String orderId, String customerId, List<OrderItem> items) {
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

        public List<OrderItem> getItems() {
            return items;
        }

        public void setItems(List<OrderItem> items) {
            this.items = items;
        }
    }

    static class OrderItem {
        private String sku;
        private int quantity;

        public OrderItem() {
            // Default constructor for Jackson
        }

        public OrderItem(String sku, int quantity) {
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
}
