package org.mjelle.quarkus.easynats.runtime.subscriber;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MessageDeserializer - Native Types")
class MessageDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ===== Primitive Wrapper Tests =====

    @Test
    @DisplayName("should deserialize UTF-8 string to Integer")
    void testIntegerDeserialization() {
        // Given
        byte[] data = "42".getBytes(StandardCharsets.UTF_8);

        // When
        Integer result = MessageDeserializer.deserialize(data, Integer.class, objectMapper);

        // Then
        assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("should deserialize Long maximum value")
    void testLongMaxValueDeserialization() {
        // Given
        byte[] data = String.valueOf(Long.MAX_VALUE).getBytes(StandardCharsets.UTF_8);

        // When
        Long result = MessageDeserializer.deserialize(data, Long.class, objectMapper);

        // Then
        assertThat(result).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("should deserialize boolean true")
    void testBooleanTrueDeserialization() {
        // Given
        byte[] data = "true".getBytes(StandardCharsets.UTF_8);

        // When
        Boolean result = MessageDeserializer.deserialize(data, Boolean.class, objectMapper);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should deserialize String payload")
    void testStringDeserialization() {
        // Given
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        // When
        String result = MessageDeserializer.deserialize(data, String.class, objectMapper);

        // Then
        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("should deserialize base64-encoded byte array")
    void testByteArrayDeserialization() {
        // Given
        byte[] originalData = {1, 2, 3};
        String base64String = Base64.getEncoder().encodeToString(originalData);
        byte[] data = base64String.getBytes(StandardCharsets.UTF_8);

        // When
        byte[] result = MessageDeserializer.deserialize(data, byte[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(originalData);
    }

    @Test
    @DisplayName("should deserialize space-separated integers to int array")
    void testIntArrayDeserialization() {
        // Given
        byte[] data = "1 2 3".getBytes(StandardCharsets.UTF_8);

        // When
        int[] result = MessageDeserializer.deserialize(data, int[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(new int[]{1, 2, 3});
    }

    @Test
    @DisplayName("should deserialize space-separated longs to long array")
    void testLongArrayDeserialization() {
        // Given
        byte[] data = "100 200 300".getBytes(StandardCharsets.UTF_8);

        // When
        long[] result = MessageDeserializer.deserialize(data, long[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(new long[]{100L, 200L, 300L});
    }

    @Test
    @DisplayName("should deserialize comma-separated strings to String array")
    void testStringArrayDeserialization() {
        // Given
        byte[] data = "a,b,c".getBytes(StandardCharsets.UTF_8);

        // When
        String[] result = MessageDeserializer.deserialize(data, String[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(new String[]{"a", "b", "c"});
    }

    // ===== Error Cases =====

    @Test
    @DisplayName("should throw DeserializationException for invalid Integer encoding")
    void testInvalidIntegerEncoding() {
        // Given
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, Integer.class, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("Integer");
    }

    @Test
    @DisplayName("should throw DeserializationException for empty data")
    void testEmptyData() {
        // Given
        byte[] data = new byte[0];

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, Integer.class, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    @DisplayName("should throw DeserializationException for null data")
    void testNullData() {
        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(null, (Class<?>) Integer.class, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("should throw DeserializationException for null targetType")
    void testNullTargetType() {
        // Given
        byte[] data = "42".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, (Class<?>) null, objectMapper))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("Target type");
    }

    @Test
    @DisplayName("should throw DeserializationException for null ObjectMapper")
    void testNullObjectMapper() {
        // Given
        byte[] data = "42".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, Integer.class, (ObjectMapper) null))
                .isInstanceOf(DeserializationException.class)
                .hasMessageContaining("ObjectMapper");
    }

    @Test
    @DisplayName("should deserialize Double type")
    void testDoubleDeserialization() {
        // Given
        byte[] data = "3.14".getBytes(StandardCharsets.UTF_8);

        // When
        Double result = MessageDeserializer.deserialize(data, Double.class, objectMapper);

        // Then
        assertThat(result).isEqualTo(3.14);
    }

    @Test
    @DisplayName("should deserialize Float type")
    void testFloatDeserialization() {
        // Given
        byte[] data = "2.71".getBytes(StandardCharsets.UTF_8);

        // When
        Float result = MessageDeserializer.deserialize(data, Float.class, objectMapper);

        // Then
        assertThat(result).isCloseTo(2.71f, within(0.01f));
    }

    @Test
    @DisplayName("should deserialize Short type")
    void testShortDeserialization() {
        // Given
        byte[] data = "32000".getBytes(StandardCharsets.UTF_8);

        // When
        Short result = MessageDeserializer.deserialize(data, Short.class, objectMapper);

        // Then
        assertThat(result).isEqualTo((short) 32000);
    }

    @Test
    @DisplayName("should deserialize Character type")
    void testCharacterDeserialization() {
        // Given
        byte[] data = "A".getBytes(StandardCharsets.UTF_8);

        // When
        Character result = MessageDeserializer.deserialize(data, Character.class, objectMapper);

        // Then
        assertThat(result).isEqualTo('A');
    }

    @Test
    @DisplayName("should deserialize Byte type")
    void testByteDeserialization() {
        // Given
        byte[] data = "127".getBytes(StandardCharsets.UTF_8);

        // When
        Byte result = MessageDeserializer.deserialize(data, Byte.class, objectMapper);

        // Then
        assertThat(result).isEqualTo((byte) 127);
    }

    @Test
    @DisplayName("should deserialize double array")
    void testDoubleArrayDeserialization() {
        // Given
        byte[] data = "1.1 2.2 3.3".getBytes(StandardCharsets.UTF_8);

        // When
        double[] result = MessageDeserializer.deserialize(data, double[].class, objectMapper);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(1.1, within(0.01));
        assertThat(result[1]).isCloseTo(2.2, within(0.01));
        assertThat(result[2]).isCloseTo(3.3, within(0.01));
    }

    @Test
    @DisplayName("should deserialize float array")
    void testFloatArrayDeserialization() {
        // Given
        byte[] data = "1.1 2.2 3.3".getBytes(StandardCharsets.UTF_8);

        // When
        float[] result = MessageDeserializer.deserialize(data, float[].class, objectMapper);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(1.1f, within(0.01f));
        assertThat(result[1]).isCloseTo(2.2f, within(0.01f));
        assertThat(result[2]).isCloseTo(3.3f, within(0.01f));
    }

    @Test
    @DisplayName("should deserialize boolean array")
    void testBooleanArrayDeserialization() {
        // Given
        byte[] data = "true false true".getBytes(StandardCharsets.UTF_8);

        // When
        boolean[] result = MessageDeserializer.deserialize(data, boolean[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(new boolean[]{true, false, true});
    }

    @Test
    @DisplayName("should deserialize short array")
    void testShortArrayDeserialization() {
        // Given
        byte[] data = "100 200 300".getBytes(StandardCharsets.UTF_8);

        // When
        short[] result = MessageDeserializer.deserialize(data, short[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(new short[]{100, 200, 300});
    }

    @Test
    @DisplayName("should deserialize char array")
    void testCharArrayDeserialization() {
        // Given
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        // When
        char[] result = MessageDeserializer.deserialize(data, char[].class, objectMapper);

        // Then
        assertThat(result).isEqualTo(new char[]{'h', 'e', 'l', 'l', 'o'});
    }

    @Test
    @DisplayName("should throw exception for invalid int array format")
    void testInvalidIntArrayFormat() {
        // Given
        byte[] data = "1 abc 3".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, int[].class, objectMapper))
                .isInstanceOf(DeserializationException.class);
    }

    @Test
    @DisplayName("should throw exception for invalid base64 in byte array")
    void testInvalidBase64ByteArray() {
        // Given
        byte[] data = "!@#$%^&*()".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, byte[].class, objectMapper))
                .isInstanceOf(DeserializationException.class);
    }

    @Test
    @DisplayName("should deserialize boolean with numeric values")
    void testBooleanWithNumericValues() {
        // Given
        byte[] dataTrue = "1".getBytes(StandardCharsets.UTF_8);
        byte[] dataFalse = "0".getBytes(StandardCharsets.UTF_8);

        // When
        Boolean resultTrue = MessageDeserializer.deserialize(dataTrue, Boolean.class, objectMapper);
        Boolean resultFalse = MessageDeserializer.deserialize(dataFalse, Boolean.class, objectMapper);

        // Then
        assertThat(resultTrue).isTrue();
        assertThat(resultFalse).isFalse();
    }

    @Test
    @DisplayName("should throw exception for invalid Character (not single char)")
    void testInvalidCharacter() {
        // Given
        byte[] data = "ab".getBytes(StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> MessageDeserializer.deserialize(data, Character.class, objectMapper))
                .isInstanceOf(DeserializationException.class);
    }
}
