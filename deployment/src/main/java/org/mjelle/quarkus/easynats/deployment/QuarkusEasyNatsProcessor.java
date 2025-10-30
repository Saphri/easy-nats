package org.mjelle.quarkus.easynats.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import jakarta.enterprise.inject.spi.DefinitionException;
import java.util.List;
import java.util.Optional;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.deployment.build.SubscriberBuildItem;
import org.mjelle.quarkus.easynats.deployment.build.SubscribersCollectionBuildItem;
import org.mjelle.quarkus.easynats.deployment.processor.SubscriberDiscoveryProcessor;
import org.mjelle.quarkus.easynats.runtime.NatsConnectionProvider;
import org.mjelle.quarkus.easynats.runtime.NatsPublisherRecorder;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistry;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistryRecorder;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;
import org.mjelle.quarkus.easynats.runtime.health.NatsHealthCheck;
import org.mjelle.quarkus.easynats.runtime.health.NatsReadinessCheck;
import org.mjelle.quarkus.easynats.runtime.health.NatsStartupCheck;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;
import org.mjelle.quarkus.easynats.runtime.startup.SubscriberInitializer;
import org.mjelle.quarkus.easynats.runtime.subscriber.TypeValidationResult;
import org.mjelle.quarkus.easynats.runtime.subscriber.TypeValidator;

class QuarkusEasyNatsProcessor {

    private static final String FEATURE = "quarkus-easy-nats";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    UnremovableBeanBuildItem unremovableTraceService() {
        // This bean is looked up programmatically, so we need to mark it as unremovable
        return UnremovableBeanBuildItem.beanClassNames(NatsTraceService.class.getName());
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem activateSslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep
    void initializeSecureRandomRelatedClassesAtRuntime(
            BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClasses) {
        runtimeInitializedClasses.produce(new RuntimeInitializedClassBuildItem("io.nats.client.support.RandomUtils"));
        runtimeInitializedClasses.produce(new RuntimeInitializedClassBuildItem("io.nats.client.NUID"));
    }

    @BuildStep
    void discoverSubscribers(
            CombinedIndexBuildItem index,
            BuildProducer<SubscriberBuildItem> subscribers,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
        try {
            SubscriberDiscoveryProcessor processor = new SubscriberDiscoveryProcessor();
            List<SubscriberMetadata> discovered = processor.discoverSubscribers(index.getIndex());
            for (SubscriberMetadata metadata : discovered) {
                subscribers.produce(new SubscriberBuildItem(metadata));
            }
        } catch (IllegalArgumentException e) {
            errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                    new DefinitionException("Subscriber validation error: " + e.getMessage())));
        }
    }

    @BuildStep
    void registerSubscribersForReflection(
            List<SubscriberBuildItem> subscriberBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        for (SubscriberBuildItem item : subscriberBuildItems) {
            SubscriberMetadata metadata = item.getMetadata();

            // Register the class that declares the subscriber method
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(metadata.declaringBeanClass()).methods().build());

            // Register the parameter types of the subscriber method
            for (String paramType : metadata.parameterTypes()) {
                reflectiveClasses.produce(ReflectiveClassBuildItem.builder(paramType).build());
            }
        }
    }

    @BuildStep
    void markSubscriberBeansAsUnremovable(
            List<SubscriberBuildItem> subscriberBuildItems,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        // Mark all beans containing @NatsSubscriber methods as unremovable
        // This prevents Arc from removing them as "unused" since they're accessed via reflection
        subscriberBuildItems.stream()
                .map(item -> item.getMetadata().declaringBeanClass())
                .distinct()
                .forEach(beanClassName ->
                    unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(beanClassName))
                );
    }

    @BuildStep
    SubscribersCollectionBuildItem aggregateSubscribers(List<SubscriberBuildItem> subscriberBuildItems) {
        List<SubscriberMetadata> metadata = subscriberBuildItems.stream()
                .map(SubscriberBuildItem::getMetadata)
                .toList();
        return new SubscribersCollectionBuildItem(metadata);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerSubscribers(
            SubscribersCollectionBuildItem subscribersCollection,
            SubscriberRegistryRecorder recorder) {
        recorder.registerSubscribers(subscribersCollection.getSubscribers());
    }

    @BuildStep
    void validateNatsPublisherInjectionPoints(ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
        DotName natsPublisherDotName = DotName.createSimple(NatsPublisher.class.getName());
        DotName natsSubjectDotName = DotName.createSimple(NatsSubject.class.getName());
        TypeValidator typeValidator = new TypeValidator();

        for (BeanInfo bean : validationPhase.getContext().beans()) {
            for (InjectionPointInfo injectionPoint : bean.getAllInjectionPoints()) {

                // We are only interested in NatsPublisher injection points
                if (!injectionPoint.getType().name().equals(natsPublisherDotName)) {
                    // Before continuing, check if this injection point has @NatsSubject, which is an error
                    for (AnnotationInstance qualifier : injectionPoint.getRequiredQualifiers()) {
                        if (qualifier.name().equals(natsSubjectDotName)) {
                            errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                                    new DefinitionException(
                                            "@NatsSubject can only be applied to fields of type NatsPublisher. Injection point: "
                                                    + injectionPoint)));
                            break; // No need to check other qualifiers
                        }
                    }
                    continue;
                }

                // From here, we know the injection point is of type NatsPublisher

                // 1. Must have @NatsSubject qualifier
                Optional<AnnotationInstance> natsSubjectAnnotation = injectionPoint.getRequiredQualifiers().stream()
                        .filter(a -> a.name().equals(natsSubjectDotName))
                        .findFirst();

                if (natsSubjectAnnotation.isEmpty()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DefinitionException(
                                    "Injection point for NatsPublisher must be annotated with @NatsSubject. Injection point: "
                                            + injectionPoint)));
                    continue; // Skip other checks for this injection point
                }

                // 2. @NatsSubject value cannot be empty
                String subject = natsSubjectAnnotation.get().value() != null
                        ? natsSubjectAnnotation.get().value().asString()
                        : null;
                if (subject == null || subject.trim().isEmpty()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DefinitionException(
                                    "@NatsSubject value cannot be empty. Injection point: " + injectionPoint)));
                }

                // 3. Must be a parameterized type
                Type injectionPointType = injectionPoint.getType();
                if (injectionPointType.kind() != Type.Kind.PARAMETERIZED_TYPE
                        || injectionPointType.asParameterizedType().arguments().isEmpty()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DefinitionException(
                                    "NatsPublisher must be parameterized with a generic type. E.g., NatsPublisher<String>. Injection point: "
                                            + injectionPoint)));
                    continue; // Skip type validation if it's not parameterized
                }

                // 4. Validate the generic type parameter
                Type genericType = injectionPointType.asParameterizedType().arguments().get(0);
                String genericTypeClassName = genericType.name().toString();

                try {
                    // Use the TCCL, as the application classes will be loaded there
                    Class<?> genericClass = Thread.currentThread().getContextClassLoader().loadClass(genericTypeClassName);
                    TypeValidationResult result = typeValidator.validate(genericClass);
                    if (!result.isValid()) {
                        errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                                new DefinitionException(
                                        String.format("Invalid type '%s' for NatsPublisher: %s. Injection point: %s",
                                                genericClass.getSimpleName(), result.getErrorMessage(), injectionPoint))));
                    }
                } catch (ClassNotFoundException e) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DefinitionException(
                                    "Could not load class for NatsPublisher generic type: " + genericTypeClassName, e)));
                }
            }
        }
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(NatsConnectionManager.class)
                .addBeanClass(NatsConnectionProvider.class)
                .addBeanClass(NatsPublisherRecorder.class)
                .addBeanClass(SubscriberRegistry.class)
                .addBeanClass(SubscriberInitializer.class)
                .addBeanClass(ConnectionStatusHolder.class)
                .addBeanClass(NatsHealthCheck.class)
                .addBeanClass(NatsReadinessCheck.class)
                .addBeanClass(NatsStartupCheck.class)
                .addBeanClass(NatsTraceService.class)
                .build();
    }
}
