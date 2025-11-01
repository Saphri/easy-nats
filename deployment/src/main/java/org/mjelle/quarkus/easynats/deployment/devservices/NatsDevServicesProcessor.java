package org.mjelle.quarkus.easynats.deployment.devservices;

import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesComposeProjectBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;

import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.testcontainers.utility.DockerImageName;

/**
 * Quarkus Dev Services processor for NATS.
 *
 * This processor automatically starts a shared NATS container in dev/test mode
 * when no NATS server URL is explicitly configured and Dev Services are enabled.
 */
@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
public class NatsDevServicesProcessor {

    private static final Logger log = Logger.getLogger(NatsDevServicesProcessor.class);
    private static final String FEATURE = "quarkus-easynats-devservices";
    private static final String NATS_URL_PROPERTY = "quarkus.easynats.servers";
    public static final String CONTAINER_LABEL = "quarkus-easynats";

    private static final ContainerLocator natsContainerLocator = new ContainerLocator(CONTAINER_LABEL, NatsContainer.NATS_PORT);

    @BuildStep
    void startNatsDevService(LaunchModeBuildItem launchMode,
            DevServicesComposeProjectBuildItem composeProjectBuildItem,
            NatsDevServicesBuildTimeConfiguration config,
            DevServicesConfig devServicesConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            BuildProducer<DevServicesResultBuildItem> devServicesResult) {
        // Check if Dev Services are explicitly disabled
        if (!config.enabled()) {
            log.info("NATS Dev Services are disabled via configuration");
            return;
        }

        log.infof("Preparing NATS Dev Services container with image: %s", config.imageName());

        try {
            DevServicesResultBuildItem discovered = discoverRunningService(composeProjectBuildItem, launchMode.getLaunchMode(), config);
            if (discovered != null) {
                devServicesResult.produce(discovered);
            } else {
                // Determine if shared network is required
                boolean useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(devServicesConfig,
                devServicesSharedNetworkBuildItem);
                String defaultNetworkId = composeProjectBuildItem.getDefaultNetworkId();

                devServicesResult.produce(DevServicesResultBuildItem.owned().name(FEATURE)
                        .serviceName(config.serviceName())
                        .startable(() -> {
                            try {
                                log.infof("Dev Services: Creating and starting NATS container (image: %s)", config.imageName());
                                NatsContainer container = new NatsContainer(
                                        DockerImageName.parse(config.imageName()),
                                        config.username(),
                                        config.password(),
                                        config.port(),
                                        useSharedNetwork,
                                        defaultNetworkId
                                );
                                if (useSharedNetwork) {
                                    log.debug("Dev Services: Adding shared service label for container reuse");
                                    container.withSharedServiceLabel(LaunchMode.DEVELOPMENT, FEATURE);
                                }

                                log.info("Dev Services: Starting NATS container...");
                                container.start();

                                String scheme = config.sslEnabled() ? "tls" : "nats";
                                String connectionUrl = scheme + "://" + container.getConnectionInfo();
                                log.infof("Dev Services: NATS container started successfully. Connection URL: %s", connectionUrl);
                                return container;
                            } catch (Exception e) {
                                log.errorf(e, "Dev Services: Failed to start NATS container");
                                throw new RuntimeException("Failed to start NATS Dev Services container", e);
                            }
                        })
                        .configProvider(buildConfigProvider(config))
                        .build());
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to prepare NATS Dev Services (error during build step): %s", e.getMessage());
            String errorMessage = String.format(
                    "Unable to start NATS Dev Services container for NATS image '%s'. " +
                    "Ensure Docker is installed and running, and the image is available. " +
                    "You can disable Dev Services by setting 'quarkus.easynats.devservices.enabled=false' in your application.properties or environment. " +
                    "Original error: %s",
                    config.imageName(), e.getMessage());
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Builds a configuration provider map from the Dev Services configuration.
     *
     * @param config the Dev Services configuration
     * @return a map of property names to configuration provider functions
     */
    private Map<String, java.util.function.Function<NatsContainer, String>> buildConfigProvider(NatsDevServicesBuildTimeConfiguration config) {
        String scheme = config.sslEnabled() ? "tls" : "nats";
        return Map.of(
                NATS_URL_PROPERTY, s -> scheme + "://" + s.getConnectionInfo(),
                "quarkus.easynats.username", s -> config.username(),
                "quarkus.easynats.password", s -> config.password(),
                "quarkus.easynats.ssl-enabled", s -> String.valueOf(config.sslEnabled())
        );
    }

    private DevServicesResultBuildItem discoverRunningService(DevServicesComposeProjectBuildItem composeProjectBuildItem,
            LaunchMode launchMode,
            NatsDevServicesBuildTimeConfiguration config) {
        String scheme = config.sslEnabled() ? "tls" : "nats";
        int port = config.port().orElse(NatsContainer.NATS_PORT);
        return natsContainerLocator
                .locateContainer("nats", true, launchMode)
                .or(() -> ComposeLocator.locateContainer(composeProjectBuildItem,
                        List.of(config.imageName()),
                        port, launchMode, false))
                .map(containerAddress -> {
                    String serverUrl = scheme + "://" + containerAddress.getUrl();
                    return DevServicesResultBuildItem.discovered()
                            .name(FEATURE)
                            .containerId(containerAddress.getId())
                            .config(Map.of("quarkus.easynats.servers", serverUrl,
                                    "quarkus.easynats.username", config.username(),
                                    "quarkus.easynats.password", config.password(),
                                    "quarkus.easynats.ssl-enabled", String.valueOf(config.sslEnabled())))
                            .build();
                }).orElse(null);
    }
}
