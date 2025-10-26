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
    private static final DotName STRING_TYPE = DotName.createSimple(String.class);

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

        // Validate parameter type is String
        Type parameterType = method.parameterType(0);
        if (!parameterType.name().equals(STRING_TYPE)) {
            throw new IllegalArgumentException(
                    String.format(
                            "@NatsSubscriber method %s.%s parameter must be String, found %s",
                            declaringClass.name(), method.name(), parameterType.name()));
        }
    }
}
