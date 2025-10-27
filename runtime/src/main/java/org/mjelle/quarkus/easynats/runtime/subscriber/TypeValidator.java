package org.mjelle.quarkus.easynats.runtime.subscriber;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates Java types for Jackson serialization/deserialization compatibility.
 *
 * Ensures that only Jackson-compatible types (POJOs with no-arg constructors, records,
 * types with Jackson annotations) are used. Rejects primitives, arrays, and types
 * that cannot be instantiated by Jackson.
 */
public class TypeValidator {

    private final ObjectMapper objectMapper;

    public TypeValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates a type for Jackson compatibility.
     *
     * @param type the Java type to validate
     * @return TypeValidationResult with validation outcome
     */
    public TypeValidationResult validate(Class<?> type) {
        if (type == null) {
            return new TypeValidationResult(
                false,
                "null",
                "Type cannot be null",
                ValidationErrorType.CUSTOM_ERROR
            );
        }

        // Check for primitive types
        if (validatePrimitiveType(type)) {
            return createPrimitiveTypeError(type);
        }

        // Check for array types
        if (validateArrayType(type)) {
            return createArrayTypeError(type);
        }

        // Try to construct Jackson type to validate introspection
        try {
            JavaType jacksonType = objectMapper.getTypeFactory().constructType(type);

            // Check if type has a no-arg constructor (for non-records)
            if (!isRecord(type) && !validateNoArgConstructor(type)) {
                return createMissingNoArgCtorError(type);
            }

            // Type is valid
            return new TypeValidationResult(true, type.getName(), null, null);
        } catch (Exception e) {
            return new TypeValidationResult(
                false,
                type.getName(),
                "Jackson type introspection failed: " + e.getMessage(),
                ValidationErrorType.JACKSON_ERROR
            );
        }
    }

    /**
     * Checks if a type is a primitive type.
     *
     * @param type the type to check
     * @return true if type is a primitive
     */
    private boolean validatePrimitiveType(Class<?> type) {
        return type.isPrimitive();
    }

    /**
     * Checks if a type is an array type.
     *
     * @param type the type to check
     * @return true if type is an array
     */
    private boolean validateArrayType(Class<?> type) {
        return type.isArray();
    }

    /**
     * Checks if a type has a no-arg constructor.
     *
     * @param type the type to check
     * @return true if type has a no-arg constructor
     */
    private boolean validateNoArgConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Checks if a type is a Java record.
     *
     * @param type the type to check
     * @return true if type is a record
     */
    private boolean isRecord(Class<?> type) {
        // Java 16+: Use isRecord() method if available
        try {
            return (boolean) type.getClass().getMethod("isRecord").invoke(type);
        } catch (Exception e) {
            // Fallback: check if type is a record by checking for java.lang.Record superclass
            return type.getSuperclass() != null &&
                   "java.lang.Record".equals(type.getSuperclass().getName());
        }
    }

    private TypeValidationResult createPrimitiveTypeError(Class<?> type) {
        String typeName = type.getName();
        String wrapper = getWrapperClassName(typeName);
        String message = String.format(
            "Primitive type '%s' is not supported. Wrap it in a POJO:\n" +
            "public class %sValue {\n" +
            "    public %s value;\n" +
            "    public %sValue() {}\n" +
            "    public %sValue(%s value) { this.value = value; }\n" +
            "}",
            typeName, wrapper, typeName, wrapper, wrapper, typeName
        );
        return new TypeValidationResult(false, typeName, message, ValidationErrorType.PRIMITIVE_TYPE);
    }

    private TypeValidationResult createArrayTypeError(Class<?> type) {
        String typeName = type.getSimpleName();
        String elementTypeName = type.getComponentType().getSimpleName();
        String message = String.format(
            "Array type '%s' is not supported. Wrap it in a POJO:\n" +
            "public class %sList {\n" +
            "    public %s[] items;\n" +
            "    public %sList() {}\n" +
            "    public %sList(%s[] items) { this.items = items; }\n" +
            "}",
            typeName, elementTypeName, typeName, elementTypeName, elementTypeName, typeName
        );
        return new TypeValidationResult(false, typeName, message, ValidationErrorType.ARRAY_TYPE);
    }

    private TypeValidationResult createMissingNoArgCtorError(Class<?> type) {
        String message = String.format(
            "Type '%s' requires a no-arg constructor for Jackson deserialization.\n" +
            "Add a no-arg constructor or use @JsonDeserialize with a custom deserializer.\n" +
            "Example:\n" +
            "public class %s {\n" +
            "    public %s() {}  // Add this no-arg constructor\n" +
            "}",
            type.getSimpleName(), type.getSimpleName(), type.getSimpleName()
        );
        return new TypeValidationResult(
            false,
            type.getName(),
            message,
            ValidationErrorType.MISSING_NO_ARG_CTOR
        );
    }

    private String getWrapperClassName(String primitiveTypeName) {
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
}
