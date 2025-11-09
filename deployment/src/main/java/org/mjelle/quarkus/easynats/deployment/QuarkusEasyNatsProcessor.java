package org.mjelle.quarkus.easynats.deployment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import jakarta.enterprise.inject.spi.DefinitionException;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.core.NatsConnectionProducer;
import org.mjelle.quarkus.easynats.deployment.build.SubscriberBuildItem;
import org.mjelle.quarkus.easynats.deployment.build.SubscribersCollectionBuildItem;
import org.mjelle.quarkus.easynats.deployment.processor.PublisherTypeValidator;
import org.mjelle.quarkus.easynats.deployment.processor.SubscriberDiscoveryProcessor;
import org.mjelle.quarkus.easynats.runtime.NatsConnectionProvider;
import org.mjelle.quarkus.easynats.runtime.NatsPublisherProducer;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistry;
import org.mjelle.quarkus.easynats.runtime.SubscriberRegistryRecorder;
import org.mjelle.quarkus.easynats.runtime.codec.DefaultCodec;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;
import org.mjelle.quarkus.easynats.runtime.health.NatsHealthCheck;
import org.mjelle.quarkus.easynats.runtime.health.NatsReadinessCheck;
import org.mjelle.quarkus.easynats.runtime.health.NatsStartupCheck;
import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;
import org.mjelle.quarkus.easynats.runtime.startup.SubscriberInitializer;

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

class QuarkusEasyNatsProcessor {

  private static final String FEATURE = "quarkus-easy-nats";
  private static final DotName NATS_SUBSCRIBER =
      DotName.createSimple(NatsSubscriber.class.getName());
  private static final DotName NATS_PUBLISHER = DotName.createSimple(NatsPublisher.class.getName());
  private static final DotName NATS_SUBJECT = DotName.createSimple(NatsSubject.class.getName());
  private static final DotName LIST = DotName.createSimple(List.class.getName());
  private static final DotName SET = DotName.createSimple(Set.class.getName());
  private static final DotName QUEUE = DotName.createSimple(Queue.class.getName());
  private static final DotName MAP = DotName.createSimple(Map.class.getName());
  private static final List<DotName> SUPPORTED_COLLECTIONS = List.of(LIST, SET, QUEUE, MAP);

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
    runtimeInitializedClasses.produce(
        new RuntimeInitializedClassBuildItem("io.nats.client.support.RandomUtils"));
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
      errors.produce(
          new ValidationPhaseBuildItem.ValidationErrorBuildItem(
              new DefinitionException(
                  """
                  Subscriber validation error: %s
                  """
                      .formatted(e.getMessage()))));
    }
  }

  @BuildStep
  void registerSubscribersForReflection(
      CombinedIndexBuildItem combinedIndex,
      BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
    IndexView index = combinedIndex.getIndex();
    Collection<AnnotationInstance> annotations = index.getAnnotations(NATS_SUBSCRIBER);

    for (AnnotationInstance annotation : annotations) {
      MethodInfo method = annotation.target().asMethod();
      reflectiveClass.produce(
          ReflectiveClassBuildItem.builder(method.declaringClass().name().toString())
              .methods()
              .build());
      for (Type parameterType : method.parameterTypes()) {
        processTypeForReflection(parameterType, reflectiveClass);
      }
    }
  }

  @BuildStep
  void validateNatsPublisherTypes(
      CombinedIndexBuildItem combinedIndex,
      ValidationPhaseBuildItem validationPhase,
      BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
    PublisherTypeValidator validator = new PublisherTypeValidator(combinedIndex.getIndex());

    for (BeanInfo bean : validationPhase.getContext().beans()) {
      for (InjectionPointInfo injectionPoint : bean.getAllInjectionPoints()) {
        Type injectionPointType = injectionPoint.getType();

        if (injectionPointType.name().equals(NATS_PUBLISHER)
            && injectionPointType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
          ParameterizedType parameterizedType = injectionPointType.asParameterizedType();
          if (!parameterizedType.arguments().isEmpty()) {
            Type payloadType = parameterizedType.arguments().get(0);
            PublisherTypeValidator.ValidationResult result = validator.validate(payloadType);

            if (!result.isValid()) {
              errors.produce(
                  new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                      new DefinitionException(
                          String.format(
                              """
                              Invalid NatsPublisher type parameter at injection point: %s

                              %s
                              """,
                              injectionPoint, result.getErrorMessage()))));
            }
          }
        }
      }
    }
  }

  @BuildStep
  void registerPublisherTypesForReflection(
      ValidationPhaseBuildItem validationPhase,
      BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
    for (BeanInfo bean : validationPhase.getContext().beans()) {
      for (InjectionPointInfo injectionPoint : bean.getAllInjectionPoints()) {
        Type injectionPointType = injectionPoint.getType();

        if (injectionPointType.name().equals(NATS_PUBLISHER)
            && injectionPointType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
          ParameterizedType parameterizedType = injectionPointType.asParameterizedType();
          if (!parameterizedType.arguments().isEmpty()) {
            Type payloadType = parameterizedType.arguments().get(0);
            processTypeForReflection(payloadType, reflectiveClass);
          }
        }
      }
    }
  }

  private void processTypeForReflection(
      Type type, BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
    if (type.kind() == Type.Kind.CLASS) {
      reflectiveClass.produce(
          ReflectiveClassBuildItem.builder(type.asClassType().name().toString())
              .constructors()
              .methods()
              .fields()
              .build());
    } else if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
      ParameterizedType parameterizedType = type.asParameterizedType();
      if (SUPPORTED_COLLECTIONS.contains(parameterizedType.name())) {
        for (Type argument : parameterizedType.arguments()) {
          processTypeForReflection(argument, reflectiveClass);
        }
      }
    } else if (type.kind() == Type.Kind.ARRAY) {
      Type componentType = type.asArrayType().constituent();
      processTypeForReflection(componentType, reflectiveClass);
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
        .forEach(
            beanClassName ->
                unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(beanClassName)));
  }

  @BuildStep
  SubscribersCollectionBuildItem aggregateSubscribers(
      List<SubscriberBuildItem> subscriberBuildItems) {
    List<SubscriberMetadata> metadata =
        subscriberBuildItems.stream().map(SubscriberBuildItem::getMetadata).toList();
    return new SubscribersCollectionBuildItem(metadata);
  }

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  void registerSubscribers(
      SubscribersCollectionBuildItem subscribersCollection, SubscriberRegistryRecorder recorder) {
    recorder.registerSubscribers(subscribersCollection.getSubscribers());
  }

  @BuildStep
  void validateNatsSubjectInjectionPoints(
      ValidationPhaseBuildItem validationPhase,
      BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
    for (BeanInfo bean : validationPhase.getContext().beans()) {
      for (InjectionPointInfo injectionPoint : bean.getAllInjectionPoints()) {
        for (AnnotationInstance qualifier : injectionPoint.getRequiredQualifiers()) {
          if (qualifier.name().equals(NATS_SUBJECT)) {
            // Validate that the injection point is a NatsPublisher
            if (!injectionPoint.getType().name().equals(NATS_PUBLISHER)) {
              errors.produce(
                  new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                      new DefinitionException(
                          """
                          @NatsSubject can only be applied to fields of type NatsPublisher.
                          Injection point: %s
                          """
                              .formatted(injectionPoint))));
            }

            // Validate that the subject value is not empty
            String subject = qualifier.value() != null ? qualifier.value().asString() : null;
            if (subject == null || subject.trim().isEmpty()) {
              errors.produce(
                  new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                      new DefinitionException(
                          """
                          NatsSubject value cannot be empty.
                          Injection point: %s
                          """
                              .formatted(injectionPoint))));
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
        .addBeanClass(NatsConnectionProducer.class)
        .addBeanClass(NatsConnectionProvider.class)
        .addBeanClass(DefaultCodec.class)
        .addBeanClass(NatsPublisherProducer.class)
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
