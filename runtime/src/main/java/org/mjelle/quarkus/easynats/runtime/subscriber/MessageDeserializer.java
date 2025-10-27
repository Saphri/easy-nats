package org.mjelle.quarkus.easynats.runtime.subscriber;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility for deserializing CloudEvent data into typed objects.
 *
 * <p>
 * Supports multiple decoder strategies matching TypedPayloadEncoder:
 * - Native types (String, primitives wrappers): Direct UTF-8 decoding
 * - Byte arrays: Base64 decoding
 * - Primitive arrays (int[], long[], etc.): Space-separated parsing
 * - String arrays: Comma-separated parsing
 * - Complex types (POJOs, records, generics): Jackson JSON deserialization
 * </p>
 */
public final class MessageDeserializer {

    private MessageDeserializer() {
        // Prevent instantiation
    }

    /**
     * Deserialize CloudEvent data into typed object using appropriate decoder.
     *
     * <p>
     * Automatically chooses decoder strategy based on target type:
     * - Native types: Direct UTF-8, base64, space-separated, or comma-separated parsing
     * - Complex types: Jackson JSON deserialization
     * </p>
     *
     * @param data extracted event data bytes
     * @param targetType the Java type to deserialize into
     * @param objectMapper Jackson ObjectMapper to use (for complex types only)
     * @return deserialized object of type T
     * @throws DeserializationException if data cannot be deserialized
     */
    public static <T> T deserialize(byte[] data, Class<T> targetType, ObjectMapper objectMapper)
            throws DeserializationException {
        if (data == null || data.length == 0) {
            throw new DeserializationException("Data cannot be null or empty");
        }
        if (targetType == null) {
            throw new DeserializationException("Target type cannot be null");
        }
        if (objectMapper == null) {
            throw new DeserializationException("ObjectMapper cannot be null");
        }

        try {
            return deserializeNative(data, targetType);
        } catch (DeserializationException e) {
            // Native deserialization failed, try Jackson for complex types
            try {
                T result = objectMapper.readValue(data, targetType);
                if (result == null) {
                    throw new DeserializationException(
                            "Deserialization resulted in null for type " + targetType.getName());
                }
                return result;
            } catch (Exception jacksonError) {
                throw new DeserializationException(
                        "Failed to deserialize data to type " + targetType.getName(),
                        jacksonError);
            }
        }
    }

    /**
     * Variant supporting generic types (List<User>, etc.).
     *
     * @param data extracted event data bytes
     * @param typeRef type reference for generic types
     * @param objectMapper Jackson ObjectMapper to use
     * @return deserialized object of type T
     * @throws DeserializationException if data cannot be deserialized
     */
    public static <T> T deserialize(
            byte[] data, TypeReference<T> typeRef, ObjectMapper objectMapper)
            throws DeserializationException {
        if (data == null || data.length == 0) {
            throw new DeserializationException("Data cannot be null or empty");
        }
        if (typeRef == null) {
            throw new DeserializationException("TypeReference cannot be null");
        }
        if (objectMapper == null) {
            throw new DeserializationException("ObjectMapper cannot be null");
        }

        try {
            T result = objectMapper.readValue(data, typeRef);
            if (result == null) {
                throw new DeserializationException(
                        "Deserialization resulted in null for type reference");
            }
            return result;
        } catch (Exception e) {
            throw new DeserializationException(
                    "Failed to deserialize data using TypeReference", e);
        }
    }

    /**
     * Attempt to deserialize using native type decoders.
     *
     * @param data the data bytes
     * @param targetType the target type
     * @return deserialized object if native decoder applies
     * @throws DeserializationException if native decoding fails or type not native
     */
    @SuppressWarnings("unchecked")
    private static <T> T deserializeNative(byte[] data, Class<T> targetType)
            throws DeserializationException {

        // String type (direct UTF-8)
        if (targetType == String.class) {
            return (T) new String(data, StandardCharsets.UTF_8);
        }

        // Primitive wrapper types (Integer, Long, Double, etc.)
        if (targetType == Integer.class) {
            try {
                String value = new String(data, StandardCharsets.UTF_8);
                return (T) Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new DeserializationException(
                        "Cannot parse data as Integer: " + e.getMessage(), e);
            }
        }

        if (targetType == Long.class) {
            try {
                String value = new String(data, StandardCharsets.UTF_8);
                return (T) Long.valueOf(value);
            } catch (NumberFormatException e) {
                throw new DeserializationException(
                        "Cannot parse data as Long: " + e.getMessage(), e);
            }
        }

        if (targetType == Double.class) {
            try {
                String value = new String(data, StandardCharsets.UTF_8);
                return (T) Double.valueOf(value);
            } catch (NumberFormatException e) {
                throw new DeserializationException(
                        "Cannot parse data as Double: " + e.getMessage(), e);
            }
        }

        if (targetType == Float.class) {
            try {
                String value = new String(data, StandardCharsets.UTF_8);
                return (T) Float.valueOf(value);
            } catch (NumberFormatException e) {
                throw new DeserializationException(
                        "Cannot parse data as Float: " + e.getMessage(), e);
            }
        }

        if (targetType == Boolean.class) {
            String value = new String(data, StandardCharsets.UTF_8).trim().toLowerCase();
            if (value.equals("true") || value.equals("1")) {
                return (T) Boolean.TRUE;
            } else if (value.equals("false") || value.equals("0")) {
                return (T) Boolean.FALSE;
            } else {
                throw new DeserializationException(
                        "Cannot parse data as Boolean: expected 'true', 'false', '0', or '1', got '"
                                + value + "'");
            }
        }

        if (targetType == Short.class) {
            try {
                String value = new String(data, StandardCharsets.UTF_8);
                return (T) Short.valueOf(value);
            } catch (NumberFormatException e) {
                throw new DeserializationException(
                        "Cannot parse data as Short: " + e.getMessage(), e);
            }
        }

        if (targetType == Character.class) {
            String value = new String(data, StandardCharsets.UTF_8);
            if (value.length() != 1) {
                throw new DeserializationException(
                        "Cannot parse data as Character: expected single character, got '"
                                + value + "'");
            }
            return (T) Character.valueOf(value.charAt(0));
        }

        // Byte types (byte[], Byte)
        if (targetType == byte[].class) {
            try {
                String base64String = new String(data, StandardCharsets.UTF_8);
                return (T) Base64.getDecoder().decode(base64String);
            } catch (IllegalArgumentException e) {
                throw new DeserializationException(
                        "Cannot decode data as base64 byte array: " + e.getMessage(), e);
            }
        }

        if (targetType == Byte.class) {
            try {
                String value = new String(data, StandardCharsets.UTF_8);
                return (T) Byte.valueOf(value);
            } catch (NumberFormatException e) {
                throw new DeserializationException(
                        "Cannot parse data as Byte: " + e.getMessage(), e);
            }
        }

        // Primitive arrays (space-separated)
        if (targetType == int[].class) {
            return (T) parseIntArray(data);
        }

        if (targetType == long[].class) {
            return (T) parseLongArray(data);
        }

        if (targetType == double[].class) {
            return (T) parseDoubleArray(data);
        }

        if (targetType == float[].class) {
            return (T) parseFloatArray(data);
        }

        if (targetType == boolean[].class) {
            return (T) parseBooleanArray(data);
        }

        if (targetType == short[].class) {
            return (T) parseShortArray(data);
        }

        if (targetType == char[].class) {
            return (T) parseCharArray(data);
        }

        // String arrays (comma-separated)
        if (targetType == String[].class) {
            String value = new String(data, StandardCharsets.UTF_8);
            return (T) value.split(",");
        }

        // Not a native type, delegate to Jackson
        throw new DeserializationException(
                "Type " + targetType.getName() + " is not natively decodable");
    }

    private static int[] parseIntArray(byte[] data) throws DeserializationException {
        try {
            String value = new String(data, StandardCharsets.UTF_8);
            String[] parts = value.split("\\s+");
            int[] result = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DeserializationException(
                    "Cannot parse data as int[]: " + e.getMessage(), e);
        }
    }

    private static long[] parseLongArray(byte[] data) throws DeserializationException {
        try {
            String value = new String(data, StandardCharsets.UTF_8);
            String[] parts = value.split("\\s+");
            long[] result = new long[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Long.parseLong(parts[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DeserializationException(
                    "Cannot parse data as long[]: " + e.getMessage(), e);
        }
    }

    private static double[] parseDoubleArray(byte[] data) throws DeserializationException {
        try {
            String value = new String(data, StandardCharsets.UTF_8);
            String[] parts = value.split("\\s+");
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DeserializationException(
                    "Cannot parse data as double[]: " + e.getMessage(), e);
        }
    }

    private static float[] parseFloatArray(byte[] data) throws DeserializationException {
        try {
            String value = new String(data, StandardCharsets.UTF_8);
            String[] parts = value.split("\\s+");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DeserializationException(
                    "Cannot parse data as float[]: " + e.getMessage(), e);
        }
    }

    private static boolean[] parseBooleanArray(byte[] data) throws DeserializationException {
        try {
            String value = new String(data, StandardCharsets.UTF_8);
            String[] parts = value.split("\\s+");
            boolean[] result = new boolean[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].toLowerCase();
                if (part.equals("true") || part.equals("1")) {
                    result[i] = true;
                } else if (part.equals("false") || part.equals("0")) {
                    result[i] = false;
                } else {
                    throw new DeserializationException(
                            "Cannot parse '" + parts[i] + "' as boolean");
                }
            }
            return result;
        } catch (DeserializationException e) {
            throw e;
        } catch (Exception e) {
            throw new DeserializationException(
                    "Cannot parse data as boolean[]: " + e.getMessage(), e);
        }
    }

    private static short[] parseShortArray(byte[] data) throws DeserializationException {
        try {
            String value = new String(data, StandardCharsets.UTF_8);
            String[] parts = value.split("\\s+");
            short[] result = new short[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Short.parseShort(parts[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DeserializationException(
                    "Cannot parse data as short[]: " + e.getMessage(), e);
        }
    }

    private static char[] parseCharArray(byte[] data) throws DeserializationException {
        String value = new String(data, StandardCharsets.UTF_8);
        return value.toCharArray();
    }
}
