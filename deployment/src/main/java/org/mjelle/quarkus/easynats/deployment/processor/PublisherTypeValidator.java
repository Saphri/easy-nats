package org.mjelle.quarkus.easynats.deployment.processor;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Validates NatsPublisher type parameters at build time using Jandex.
 *
 * <p>This validator checks that generic type parameters for NatsPublisher are compatible with
 * Jackson serialization/deserialization. It rejects primitives, arrays, and types without no-arg
 * constructors (except records).
 */
public class PublisherTypeValidator {

  private final IndexView index;

  public PublisherTypeValidator(IndexView index) {
    this.index = index;
  }

  /**
   * Validates a type for Jackson compatibility.
   *
   * @param type the Jandex type to validate
   * @return validation result with error message if invalid
   */
  public ValidationResult validate(Type type) {
    if (type == null) {
      return ValidationResult.error("Type cannot be null");
    }

    // Check for primitive types
    if (type.kind() == Type.Kind.PRIMITIVE) {
      return createPrimitiveTypeError(type);
    }

    // Check for array types
    if (type.kind() == Type.Kind.ARRAY) {
      return createArrayTypeError(type);
    }

    // For class types, validate constructors
    if (type.kind() == Type.Kind.CLASS) {
      ClassInfo classInfo = index.getClassByName(type.name());
      if (classInfo == null) {
        // Class not in index - likely from external dependency, assume it's valid
        return ValidationResult.valid();
      }

      // Check if it's a record (records don't need no-arg constructor)
      if (isRecord(classInfo)) {
        return ValidationResult.valid();
      }

      // Check for no-arg constructor
      if (!hasNoArgConstructor(classInfo)) {
        return createMissingNoArgCtorError(type);
      }

      return ValidationResult.valid();
    }

    // For parameterized types (e.g., List<String>), validate the raw type
    if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
      // The parameterized type itself should be fine, but we validate the raw type
      Type rawType =
          type.asParameterizedType().name().toString().equals("java.util.List")
                  || type.asParameterizedType().name().toString().equals("java.util.Set")
                  || type.asParameterizedType().name().toString().equals("java.util.Map")
              ? Type.create(type.asParameterizedType().name(), Type.Kind.CLASS)
              : type;
      return ValidationResult.valid();
    }

    // Type variables and wildcards cannot be validated at build time
    if (type.kind() == Type.Kind.TYPE_VARIABLE || type.kind() == Type.Kind.WILDCARD_TYPE) {
      return ValidationResult.error(
          String.format(
              "Unresolvable generic type '%s'. Use a concrete type instead of wildcards or type variables.",
              type));
    }

    // All other types are considered valid
    return ValidationResult.valid();
  }

  private boolean isPrimitive(Type type) {
    return type.kind() == Type.Kind.PRIMITIVE;
  }

  private boolean isArray(Type type) {
    return type.kind() == Type.Kind.ARRAY;
  }

  private boolean isRecord(ClassInfo classInfo) {
    // Check if the class extends java.lang.Record
    DotName recordName = DotName.createSimple("java.lang.Record");
    if (classInfo.superName() != null && classInfo.superName().equals(recordName)) {
      return true;
    }
    return false;
  }

  private boolean hasNoArgConstructor(ClassInfo classInfo) {
    // Check for no-arg constructor
    for (MethodInfo method : classInfo.methods()) {
      if (method.name().equals("<init>") && method.parametersCount() == 0) {
        return true;
      }
    }
    return false;
  }

  private ValidationResult createPrimitiveTypeError(Type type) {
    String typeName = type.name().toString();
    String wrapper = getWrapperClassName(typeName);
    String message =
        String.format(
            """
            Primitive type '%s' is not supported for NatsPublisher. Wrap it in a POJO:

            public class %sValue {
                public %s value;
                public %sValue() {}
                public %sValue(%s value) { this.value = value; }
            }

            Then use: NatsPublisher<%sValue>
            """,
            typeName, wrapper, typeName, wrapper, wrapper, typeName, wrapper);
    return ValidationResult.error(message);
  }

  private ValidationResult createArrayTypeError(Type type) {
    String typeName = type.asArrayType().constituent().name().toString();
    String elementTypeName = typeName.substring(typeName.lastIndexOf('.') + 1);
    String message =
        String.format(
            """
            Array type '%s[]' is not supported for NatsPublisher. Wrap it in a POJO:

            public class %sList {
                public %s[] items;
                public %sList() {}
                public %sList(%s[] items) { this.items = items; }
            }

            Then use: NatsPublisher<%sList>
            """,
            elementTypeName,
            elementTypeName,
            elementTypeName,
            elementTypeName,
            elementTypeName,
            elementTypeName,
            elementTypeName);
    return ValidationResult.error(message);
  }

  private ValidationResult createMissingNoArgCtorError(Type type) {
    String typeName = type.name().toString();
    String simpleTypeName = typeName.substring(typeName.lastIndexOf('.') + 1);
    String message =
        String.format(
            """
            Type '%s' requires a no-arg constructor for Jackson deserialization.

            Add a no-arg constructor:
            public class %s {
                public %s() {}  // Add this no-arg constructor
            }

            Or use @JsonDeserialize with a custom deserializer.
            """,
            simpleTypeName, simpleTypeName, simpleTypeName);
    return ValidationResult.error(message);
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

  /** Represents the result of type validation. */
  public static class ValidationResult {
    private final boolean valid;
    private final String errorMessage;

    private ValidationResult(boolean valid, String errorMessage) {
      this.valid = valid;
      this.errorMessage = errorMessage;
    }

    public static ValidationResult valid() {
      return new ValidationResult(true, null);
    }

    public static ValidationResult error(String message) {
      return new ValidationResult(false, message);
    }

    public boolean isValid() {
      return valid;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
