package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TypedPayloadEncoderTest {

    @Inject
    ObjectMapper objectMapper;

    // Tests for canEncodeNatively()

    @Test
    void testCanEncodeNativelyForInt() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(Integer.class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForLong() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(Long.class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForDouble() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(Double.class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForString() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(String.class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForByteArray() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(byte[].class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForByte() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(Byte.class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForIntArray() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(int[].class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyForStringArray() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(String[].class)).isTrue();
    }

    @Test
    void testCanEncodeNativelyReturnsFalseForCustomClass() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(TestPojo.class)).isFalse();
    }

    @Test
    void testCanEncodeNativelyReturnsFalseForList() {
        assertThat(TypedPayloadEncoder.canEncodeNatively(java.util.List.class)).isFalse();
    }

    // Tests for encodeNatively()

    @Test
    void testEncodeNativelyForInteger() {
        byte[] result = TypedPayloadEncoder.encodeNatively(42);
        assertThat(result).isEqualTo("42".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForString() {
        byte[] result = TypedPayloadEncoder.encodeNatively("hello");
        assertThat(result).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForLong() {
        byte[] result = TypedPayloadEncoder.encodeNatively(123456789L);
        assertThat(result).isEqualTo("123456789".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForBoolean() {
        byte[] result = TypedPayloadEncoder.encodeNatively(true);
        assertThat(result).isEqualTo("true".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForByteArray() {
        byte[] input = new byte[]{1, 2, 3};
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        String expected = Base64.getEncoder().encodeToString(input);
        assertThat(result).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForSingleByte() {
        byte input = (byte) 42;
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        String expected = Base64.getEncoder().encodeToString(new byte[]{input});
        assertThat(result).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForIntArray() {
        int[] input = new int[]{1, 2, 3};
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        assertThat(result).isEqualTo("1 2 3".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForLongArray() {
        long[] input = new long[]{10L, 20L, 30L};
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        assertThat(result).isEqualTo("10 20 30".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForStringArray() {
        String[] input = new String[]{"a", "b", "c"};
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        assertThat(result).isEqualTo("a,b,c".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForEmptyIntArray() {
        int[] input = new int[]{};
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        assertThat(result).isEqualTo("".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForEmptyByteArray() {
        byte[] input = new byte[]{};
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        String expected = Base64.getEncoder().encodeToString(input);
        assertThat(result).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNativelyForUnicodeString() {
        String input = "hello 世界";
        byte[] result = TypedPayloadEncoder.encodeNatively(input);
        assertThat(result).isEqualTo(input.getBytes(StandardCharsets.UTF_8));
    }

    // Tests for encodeWithJackson()

    @Test
    void testEncodeWithJacksonForSimplePojo() throws Exception {
        TestPojo pojo = new TestPojo("value1", 42);
        byte[] result = TypedPayloadEncoder.encodeWithJackson(pojo, objectMapper);

        // Verify it's valid JSON
        TestPojo deserialized = objectMapper.readValue(result, 0, result.length, TestPojo.class);
        assertThat(deserialized.field1).isEqualTo("value1");
        assertThat(deserialized.field2).isEqualTo(42);
    }

    @Test
    void testEncodeWithJacksonThrowsSerializationExceptionForInvalidObject() {
        NonSerializableClass obj = new NonSerializableClass("required_param");
        assertThatThrownBy(() -> TypedPayloadEncoder.encodeWithJackson(obj, objectMapper))
            .isInstanceOf(SerializationException.class)
            .hasMessageContaining("Failed to serialize NonSerializableClass");
    }

    // Tests for resolveEncoder()

    @Test
    void testResolveEncoderForString() {
        TypedPayloadEncoder.PayloadEncoderStrategy strategy =
            TypedPayloadEncoder.resolveEncoder(String.class);
        assertThat(strategy).isEqualTo(TypedPayloadEncoder.PayloadEncoderStrategy.NATIVE_ENCODER);
    }

    @Test
    void testResolveEncoderForInt() {
        TypedPayloadEncoder.PayloadEncoderStrategy strategy =
            TypedPayloadEncoder.resolveEncoder(Integer.class);
        assertThat(strategy).isEqualTo(TypedPayloadEncoder.PayloadEncoderStrategy.NATIVE_ENCODER);
    }

    @Test
    void testResolveEncoderForByteArray() {
        TypedPayloadEncoder.PayloadEncoderStrategy strategy =
            TypedPayloadEncoder.resolveEncoder(byte[].class);
        assertThat(strategy).isEqualTo(TypedPayloadEncoder.PayloadEncoderStrategy.NATIVE_ENCODER);
    }

    @Test
    void testResolveEncoderForCustomClass() {
        TypedPayloadEncoder.PayloadEncoderStrategy strategy =
            TypedPayloadEncoder.resolveEncoder(TestPojo.class);
        assertThat(strategy).isEqualTo(TypedPayloadEncoder.PayloadEncoderStrategy.JACKSON_ENCODER);
    }

    // Test helper classes

    static class TestPojo {
        public String field1;
        public int field2;

        TestPojo() {
            // Default constructor for Jackson
        }

        TestPojo(String field1, int field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    static class NonSerializableClass {
        // No zero-arg constructor - will fail serialization
        public NonSerializableClass(String required) {
            // intentionally left empty
        }
    }
}
