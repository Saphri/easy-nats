package org.mjelle.quarkus.easynats.deployment.processor;

import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

/**
 * Discovers methods annotated with {@code @NatsSubscriber} at build time using Jandex.
 *
 * <p>
 * This processor scans the application classpath for {@code @NatsSubscriber} annotations and
 * validates the annotated methods according to the specification.
 * </p>
 */
public class SubscriberDiscoveryProcessor {

    private static final Logger LOGGER = Logger.getLogger(SubscriberDiscoveryProcessor.class);

    private static final DotName NATS_SUBSCRIBER =
            DotName.createSimple("org.mjelle.quarkus.easynats.NatsSubscriber");

    // Primitive wrapper types and supported native types
    private static final Set<DotName> SUPPORTED_NATIVE_TYPES = new HashSet<>();

    static {
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(String.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Integer.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Long.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Double.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Float.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Boolean.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Short.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Character.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(Byte.class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(byte[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(int[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(long[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(double[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(float[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(boolean[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(short[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(char[].class));
        SUPPORTED_NATIVE_TYPES.add(DotName.createSimple(String[].class));
    }

    /**
     * Discovers all methods annotated with {@code @NatsSubscriber}.
     *
     * @param index the Jandex index view for the application
     * @return list of {@link SubscriberMetadata} for each discovered subscriber
     * @throws IllegalArgumentException if validation fails
     */
    public List<SubscriberMetadata> discoverSubscribers(IndexView index) {
        List<SubscriberMetadata> subscribers = new ArrayList<>();

        List<AnnotationInstance> annotations = new ArrayList<>(index.getAnnotations(NATS_SUBSCRIBER));

        LOGGER.infof("Found %d @NatsSubscriber annotations", annotations.size());

        for (AnnotationInstance annotation : annotations) {
            AnnotationTarget target = annotation.target();

            if (target.kind() != AnnotationTarget.Kind.METHOD) {
                throw new IllegalArgumentException(
                        "@NatsSubscriber can only be applied to methods");
            }

            MethodInfo method = target.asMethod();
            ClassInfo declaringClass = method.declaringClass();

            // Validate subject value
            String subject = annotation.value().asString();
            if (subject == null || subject.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format(
                                "Empty or null subject for @NatsSubscriber on method %s.%s",
                                declaringClass.name(), method.name()));
            }

            // Validate method signature
            validateMethodSignature(method, declaringClass);

            List<String> parameterTypes = method.parameterTypes().stream()
                .map(Type::name)
                .map(DotName::toString)
                .toList();

            SubscriberMetadata metadata =
                    new SubscriberMetadata(
                            subject,
                            method.declaringClass().name().toString(),
                            method.name(),
                            declaringClass.name().toString(),
                            parameterTypes);

            subscribers.add(metadata);

            LOGGER.infof(
                    "Discovered subscriber: subject=%s, method=%s.%s",
                    subject, declaringClass.name(), method.name());
        }

        return subscribers;
    }

    /**
     * Validates that the subscriber method has the correct signature.
     *
     * <p>
     * Validates that:
     * 1. Method has exactly 1 parameter
     * 2. Parameter type is either:
     *    - A native supported type (String, Integer, Long, byte[], int[], etc.)
     *    - A Jackson-deserializable complex type (POJO, record, generic)
     * </p>
     *
     * @param method the method to validate
     * @param declaringClass the declaring class
     * @throws IllegalArgumentException if validation fails
     */
    private void validateMethodSignature(MethodInfo method, ClassInfo declaringClass) {
        // Validate exactly one parameter
        if (method.parametersCount() != 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "@NatsSubscriber method %s.%s must have exactly one parameter, found %d",
                            declaringClass.name(), method.name(), method.parametersCount()));
        }

        Type parameterType = method.parameterType(0);
        DotName paramTypeName = parameterType.name();

        // Check if it's a supported native type (including native arrays)
        if (SUPPORTED_NATIVE_TYPES.contains(paramTypeName)) {
            LOGGER.debugf(
                    "Parameter type %s is a supported native type for method %s.%s",
                    paramTypeName, declaringClass.name(), method.name());
            return;
        }

        // Check for primitive types (not allowed, use wrapper types instead)
        if (isPrimitive(paramTypeName)) {
            throw new IllegalArgumentException(
                    String.format(
                            "@NatsSubscriber method %s.%s parameter %s is a raw primitive; use wrapper type %s instead",
                            declaringClass.name(), method.name(), paramTypeName,
                            getPrimitiveWrapperType(paramTypeName)));
        }

        // For complex types, validate with Jackson TypeFactory if possible
        // Skip validation if the class cannot be loaded at build time (e.g., integration test classes)
        String typeString = paramTypeName.toString();
        Class<?> typeClass;
        try {
            typeClass = Class.forName(typeString);
        } catch (ClassNotFoundException e) {
            // Type not available at build time - this is expected for integration test classes
            // Trust that the user type is properly Jackson-deserializable
            LOGGER.debugf(
                    "Parameter type %s not available at build time; skipping deserialization validation for method %s.%s",
                    paramTypeName, declaringClass.name(), method.name());
            return;
        }

        // Check if it's a record - records are inherently Jackson-deserializable
        if (typeClass.isRecord()) {
            LOGGER.debugf(
                    "Parameter type %s is a Java record (Jackson-deserializable) for method %s.%s",
                    paramTypeName, declaringClass.name(), method.name());
            return;
        }

        // For non-record complex types, validate with Jackson TypeFactory
        try {
            TypeFactory typeFactory = TypeFactory.defaultInstance();
            typeFactory.constructType(typeClass);
            LOGGER.debugf(
                    "Parameter type %s is Jackson-deserializable for method %s.%s",
                    paramTypeName, declaringClass.name(), method.name());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format(
                            "@NatsSubscriber method %s.%s parameter %s is not Jackson-deserializable; ensure it has a no-arg constructor or @JsonCreator annotation. Details: %s",
                            declaringClass.name(), method.name(), paramTypeName, e.getMessage()),
                    e);
        }
    }

    /**
     * Checks if a type is a primitive type (int, long, boolean, etc.).
     *
     * @param typeName the type name
     * @return true if the type is a primitive type
     */
    private boolean isPrimitive(DotName typeName) {
        String name = typeName.toString();
        return name.equals("int") || name.equals("long") || name.equals("boolean")
                || name.equals("double") || name.equals("float") || name.equals("short")
                || name.equals("char") || name.equals("byte");
    }

    /**
     * Gets the wrapper type name for a primitive type.
     *
     * @param primitiveTypeName the primitive type name
     * @return the wrapper type name
     */
    private String getPrimitiveWrapperType(DotName primitiveTypeName) {
        return switch (primitiveTypeName.toString()) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "boolean" -> "Boolean";
            case "double" -> "Double";
            case "float" -> "Float";
            case "short" -> "Short";
            case "char" -> "Character";
            case "byte" -> "Byte";
            default -> "Object";
        };
    }
}
