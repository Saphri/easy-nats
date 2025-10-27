package org.mjelle.quarkus.easynats.runtime;

/**
 * Generates clear, actionable error messages for type validation failures.
 *
 * Formats error messages with guidance for users to implement the wrapper pattern
 * for unsupported types.
 */
public class ErrorMessageFormatter {

    /**
     * Formats error message for primitive type rejection.
     *
     * @param type the primitive type (int, long, etc.)
     * @return formatted error message with wrapper example
     */
    public static String formatPrimitiveTypeError(Class<?> type) {
        String typeName = type.getName();
        String wrapper = getWrapperClassName(typeName);
        return String.format("""
            Primitive type '%s' is not supported. Wrap it in a POJO:
            public class %sValue {
                public %s value;
                public %sValue() {}
                public %sValue(%s value) { this.value = value; }
            }

            Then use TypedPublisher<%sValue> instead of TypedPublisher<%s>""",
            typeName, wrapper, typeName, wrapper, wrapper, typeName, wrapper, wrapper
        );
    }

    /**
     * Formats error message for array type rejection.
     *
     * @param type the array type (int[], String[], etc.)
     * @return formatted error message with wrapper example
     */
    public static String formatArrayTypeError(Class<?> type) {
        String typeName = type.getSimpleName();
        String elementTypeName = type.getComponentType().getSimpleName();
        return String.format("""
            Array type '%s' is not supported. Wrap it in a POJO:
            public class %sList {
                public %s[] items;
                public %sList() {}
                public %sList(%s[] items) { this.items = items; }
            }

            Then use TypedPublisher<%sList> instead of TypedPublisher<%s>""",
            typeName, elementTypeName, typeName, elementTypeName, elementTypeName,
            typeName, elementTypeName, typeName
        );
    }

    /**
     * Formats error message for missing no-arg constructor.
     *
     * @param type the type without no-arg constructor
     * @return formatted error message with fix example
     */
    public static String formatMissingNoArgCtorError(Class<?> type) {
        String simpleName = type.getSimpleName();
        return String.format("""
            Type '%s' requires a no-arg constructor for Jackson deserialization.

            Add a no-arg constructor:
            public class %s {
                // ... fields ...
                public %s() {}  // Add this no-arg constructor
                // ... other constructors and methods ...
            }

            OR use @JsonDeserialize with a custom deserializer:
            @JsonDeserialize(using = Custom%sDeserializer.class)
            public class %s { ... }""",
            simpleName, simpleName, simpleName, simpleName, simpleName
        );
    }

    /**
     * Formats error message for unresolvable generic type parameter.
     *
     * @param type the type with unresolvable generic parameter
     * @return formatted error message with guidance
     */
    public static String formatUnresolvableGenericError(Class<?> type) {
        return String.format("""
            Type '%s' has unresolvable generic parameter.
            Provide concrete type instead of wildcard or unresolvable generic:

            Example:
            // ✗ WRONG: Generic type parameter not resolvable
            TypedPublisher<Container<?>> publisher;

            // ✓ CORRECT: Concrete type
            TypedPublisher<Container<String>> publisher;""",
            type.getSimpleName()
        );
    }

    /**
     * Formats error message for Jackson type construction failure.
     *
     * @param type the type that failed Jackson introspection
     * @param jacksonError the Jackson error message
     * @return formatted error message with guidance
     */
    public static String formatJacksonError(Class<?> type, String jacksonError) {
        return String.format("""
            Type '%s' failed Jackson type introspection: %s

            This usually means:
            1. The type has an unresolvable generic parameter
            2. A custom deserializer annotation is malformed
            3. The type structure is incompatible with Jackson

            Try wrapping the type in a Jackson-compatible POJO or adding a custom deserializer.""",
            type.getSimpleName(), jacksonError
        );
    }

    /**
     * Formats a deserialization error message.
     *
     * @param typeName the target type name
     * @param rawPayload the raw JSON payload that failed to deserialize
     * @param rootCause the Jackson deserialization error message
     * @return formatted error message for logging
     */
    public static String formatDeserializationError(
        String typeName,
        String rawPayload,
        String rootCause
    ) {
        String truncatedPayload = truncatePayload(rawPayload, 1000);
        return String.format("""
            Failed to deserialize to type '%s':
              Root cause: %s
              Raw payload: %s

            Common fixes:
              1. Ensure type '%s' has a no-arg constructor
              2. Check JSON structure matches type fields
              3. Use @JsonProperty for custom field name mapping
              4. Use @JsonDeserialize for custom deserialization logic""",
            typeName, rootCause, truncatedPayload, typeName
        );
    }

    /**
     * Formats a serialization error message.
     *
     * @param typeName the type that failed to serialize
     * @param rootCause the Jackson serialization error message
     * @return formatted error message for logging
     */
    public static String formatSerializationError(String typeName, String rootCause) {
        return String.format("""
            Failed to serialize type '%s':
              Root cause: %s

            Common fixes:
              1. Check for circular object references
              2. Use @JsonIgnore for transient fields
              3. Use @JsonSerialize for custom serialization
              4. Ensure all fields are Jackson-serializable""",
            typeName, rootCause
        );
    }

    private static String getWrapperClassName(String primitiveTypeName) {
        return switch (primitiveTypeName) {
            case "int" -> "Int";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "char" -> "Char";
            default -> "Value";
        };
    }

    private static String truncatePayload(String payload, int maxLength) {
        if (payload == null) {
            return "[null payload]";
        }
        if (payload.length() > maxLength) {
            return payload.substring(0, maxLength) + "... [truncated]";
        }
        return payload;
    }
}
