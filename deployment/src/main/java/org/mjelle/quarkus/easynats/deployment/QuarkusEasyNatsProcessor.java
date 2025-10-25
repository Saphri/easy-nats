package org.mjelle.quarkus.easynats.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;

class QuarkusEasyNatsProcessor {

    private static final String FEATURE = "quarkus-easy-nats";
    
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(NatsConnectionManager.class)
                .addBeanClass(NatsPublisher.class)
                .build();
    }
}
