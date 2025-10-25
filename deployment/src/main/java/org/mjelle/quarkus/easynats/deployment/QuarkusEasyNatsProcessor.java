package org.mjelle.quarkus.easynats.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuarkusEasyNatsProcessor {

    private static final String FEATURE = "quarkus-easy-nats";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
