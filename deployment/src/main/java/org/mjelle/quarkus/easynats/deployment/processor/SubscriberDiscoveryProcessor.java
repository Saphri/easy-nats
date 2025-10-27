package org.mjelle.quarkus.easynats.deployment.processor;

import java.util.ArrayList;
import java.util.List;
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
     * 2. Parameter type is Jackson-compatible (not primitive, not array, has no-arg constructor)
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

        // Validate Jackson compatibility: reject primitives and arrays at build time
        validateJacksonCompatibility(parameterType, paramTypeName, method, declaringClass);

        LOGGER.debugf(
                "Parameter type %s is Jackson-compatible for method %s.%s",
                paramTypeName, declaringClass.name(), method.name());
    }

    /**
     * Validates that a parameter type is Jackson-compatible.
     *
     * <p>
     * Rejects:
     * - Primitive types (int, long, double, etc.)
     * - Array types (int[], String[], etc.)
     * </p>
     *
     * For complex types that might not have no-arg constructors,
     * validation will happen at runtime during subscriber initialization.
     *
     * @param parameterType the parameter type to validate
     * @param paramTypeName the DotName of the parameter type
     * @param method the method being validated
     * @param declaringClass the class declaring the method
     * @throws IllegalArgumentException if the type is not Jackson-compatible
     */
    private void validateJacksonCompatibility(
            Type parameterType,
            DotName paramTypeName,
            MethodInfo method,
            ClassInfo declaringClass) {
        // Check for primitive types
        if (isPrimitiveType(paramTypeName)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Primitive type '%s' is not supported for @NatsSubscriber parameter in method %s.%s. " +
                            "Wrap it in a POJO: public class %sValue { public %s value; public %sValue() {}; }",
                            paramTypeName,
                            declaringClass.name(), method.name(),
                            getPrimitiveWrapperName(paramTypeName),
                            paramTypeName,
                            getPrimitiveWrapperName(paramTypeName)));
        }

        // Check for array types
        if (parameterType.kind() == Type.Kind.ARRAY) {
            Type componentType = parameterType.asArrayType().componentType();
            throw new IllegalArgumentException(
                    String.format(
                            "Array type '%s' is not supported for @NatsSubscriber parameter in method %s.%s. " +
                            "Wrap it in a POJO: public class %sList { public %s[] items; public %sList() {}; }",
                            paramTypeName,
                            declaringClass.name(), method.name(),
                            componentType.name(),
                            componentType.name(),
                            componentType.name()));
        }
    }

    /**
     * Checks if a type name represents a primitive type.
     */
    private boolean isPrimitiveType(DotName typeName) {
        String name = typeName.toString();
        return name.equals("int") || name.equals("long") || name.equals("double") ||
                name.equals("float") || name.equals("boolean") || name.equals("byte") ||
                name.equals("short") || name.equals("char");
    }

    /**
     * Gets the wrapper class name for a primitive type.
     */
    private String getPrimitiveWrapperName(DotName primitiveType) {
        String name = primitiveType.toString();
        return switch (name) {
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
