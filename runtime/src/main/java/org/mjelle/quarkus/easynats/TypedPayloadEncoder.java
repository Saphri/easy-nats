package org.mjelle.quarkus.easynats;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for encoding payloads with a priority-based strategy.
 *
 * Resolution order:
 * 1. Primitive wrappers and String (direct UTF-8 encoding)
 * 2. Byte types (base64-encoded, NEVER raw binary)
 * 3. Primitive/String arrays (UTF-8 space/comma-separated)
 * 4. Jackson for complex types (JSON serialization)
 */
public class TypedPayloadEncoder {

    private TypedPayloadEncoder() {
        // Utility class; no instances
    }

    /**
     * Enum representing the encoder strategy to use.
     */
    public enum PayloadEncoderStrategy {
        NATIVE_ENCODER,
        JACKSON_ENCODER
    }

    /**
     * Check if a type can be encoded without Jackson.
     *
     * @param type the class to check
     * @return true if the type can be encoded natively (primitives, String, arrays)
     */
    public static boolean canEncodeNatively(Class<?> type) {
        // Primitive wrappers
        if (type == Integer.class || type == Long.class || type == Double.class ||
            type == Float.class || type == Boolean.class || type == Short.class ||
            type == Byte.class || type == Character.class) {
            return true;
        }

        // String
        if (type == String.class) {
            return true;
        }

        // Byte types
        if (type == byte.class || type == Byte.class || type == byte[].class) {
            return true;
        }

        // Primitive arrays
        if (type == int[].class || type == long[].class || type == double[].class ||
            type == float[].class || type == boolean[].class || type == short[].class ||
            type == char[].class) {
            return true;
        }

        // String array
        if (type == String[].class) {
            return true;
        }

        return false;
    }

    /**
     * Encode a native type to bytes without Jackson.
     *
     * @param value the object to encode (must be verified as native-encodable)
     * @return UTF-8 encoded byte array
     */
    public static byte[] encodeNatively(Object value) {
        if (value == null) {
            return new byte[0];
        }

        // Handle byte array specially: base64-encode
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return encoded.getBytes(StandardCharsets.UTF_8);
        }

        // Handle single byte: base64-encode
        if (value instanceof Byte) {
            byte singleByte = (Byte) value;
            String encoded = Base64.getEncoder().encodeToString(new byte[]{singleByte});
            return encoded.getBytes(StandardCharsets.UTF_8);
        }

        // Handle primitive arrays: space-separated
        if (value instanceof int[]) {
            int[] arr = (int[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof long[]) {
            long[] arr = (long[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof double[]) {
            double[] arr = (double[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof float[]) {
            float[] arr = (float[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof boolean[]) {
            boolean[] arr = (boolean[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof short[]) {
            short[] arr = (short[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        if (value instanceof char[]) {
            char[] arr = (char[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        // Handle String array: comma-separated
        if (value instanceof String[]) {
            String[] arr = (String[]) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(arr[i]);
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        // Default: toString() and UTF-8 encode
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encode a complex type to JSON using Jackson.
     *
     * @param value the object to serialize
     * @param mapper the Jackson ObjectMapper
     * @return JSON-encoded byte array
     * @throws SerializationException if serialization fails
     */
    public static byte[] encodeWithJackson(Object value, ObjectMapper mapper) throws SerializationException {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            String className = value.getClass().getSimpleName();
            throw new SerializationException(
                "Failed to serialize " + className + ": " + e.getMessage(), e
            );
        }
    }

    /**
     * Resolve the appropriate encoder strategy for a type.
     *
     * @param type the class to check
     * @return the encoder strategy to use
     */
    public static PayloadEncoderStrategy resolveEncoder(Class<?> type) {
        if (canEncodeNatively(type)) {
            return PayloadEncoderStrategy.NATIVE_ENCODER;
        }
        return PayloadEncoderStrategy.JACKSON_ENCODER;
    }
}
