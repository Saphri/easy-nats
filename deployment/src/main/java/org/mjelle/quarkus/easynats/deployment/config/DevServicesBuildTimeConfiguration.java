package org.mjelle.quarkus.easynats.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "quarkus.easynats.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface DevServicesBuildTimeConfiguration {
    /**
     * If Dev Services for NATS is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The NATS servers to connect to.
     */
    Optional<String> servers();
}
