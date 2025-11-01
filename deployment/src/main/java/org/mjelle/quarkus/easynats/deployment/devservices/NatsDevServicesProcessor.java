package org.mjelle.quarkus.easynats.deployment.devservices;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem.RunningDevService;
import org.mjelle.quarkus.easynats.deployment.config.DevServicesBuildTimeConfiguration;

import java.util.Collections;
import java.util.Map;

class NatsDevServicesProcessor {

    private static final String NATS_IMAGE = "nats:2.11";

    @BuildStep
    DevServicesResultBuildItem startNats(DevServicesBuildTimeConfiguration configuration) {
        if (!configuration.enabled() || configuration.servers().isPresent()) {
            return null;
        }

        NatsContainer container = new NatsContainer(NATS_IMAGE);
        container.start();

        Map<String, String> config = Collections.singletonMap("quarkus.easynats.servers", container.getNatsUrl());

        return new RunningDevService("nats", container.getContainerId(), container::close, config).toBuildItem();
    }
}
