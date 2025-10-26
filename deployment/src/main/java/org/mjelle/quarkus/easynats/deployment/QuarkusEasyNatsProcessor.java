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
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import jakarta.enterprise.inject.spi.DefinitionException;
import java.util.List;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.deployment.build.SubscriberBuildItem;
import org.mjelle.quarkus.easynats.deployment.build.SubscribersCollectionBuildItem;
import org.mjelle.quarkus.easynats.deployment.processor.SubscriberDiscoveryProcessor;
import org.mjelle.quarkus.easynats.runtime.NatsPublisherRecorder;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistry;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistryRecorder;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.startup.SubscriberInitializer;

class QuarkusEasyNatsProcessor {

    private static final String FEATURE = "quarkus-easy-nats";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
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
    void markSubscriberBeansAsUnremovable(
            List<SubscriberBuildItem> subscriberBuildItems,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        // Mark all beans containing @NatsSubscriber methods as unremovable
        // This prevents Arc from removing them as "unused" since they're accessed via reflection
        subscriberBuildItems.stream()
                .map(item -> item.getMetadata().declaringBeanClass())
                .distinct()
                .forEach(beanClassName -> {
                    unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(beanClassName));
                });
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
    void validateNatsSubjectInjectionPoints(ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
        DotName natsPublisherDotName = DotName.createSimple(NatsPublisher.class.getName());

        for (BeanInfo bean : validationPhase.getContext().beans()) {
            for (InjectionPointInfo injectionPoint : bean.getAllInjectionPoints()) {
                for (AnnotationInstance qualifier : injectionPoint.getRequiredQualifiers()) {
                    if (qualifier.name().equals(DotName.createSimple(NatsSubject.class.getName()))) {
                        // Validate that the injection point is a NatsPublisher
                        if (!injectionPoint.getType().name().equals(natsPublisherDotName)) {
                            errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                                    new DefinitionException(
                                            "@NatsSubject can only be applied to fields of type NatsPublisher. Injection point: "
                                                    + injectionPoint)));
                        }

                        // Validate that the subject value is not empty
                        String subject = qualifier.value() != null ? qualifier.value().asString() : null;
                        if (subject == null || subject.trim().isEmpty()) {
                            errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                                    new DefinitionException(
                                            "NatsSubject value cannot be empty. Injection point: " + injectionPoint)));
                        }
                    }
                }
            }
        }
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(NatsConnectionManager.class)
                .addBeanClass(NatsPublisherRecorder.class)
                .addBeanClass(SubscriberRegistry.class)
                .addBeanClass(SubscriberInitializer.class)
                .build();
    }
}
