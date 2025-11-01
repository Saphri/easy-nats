package org.mjelle.quarkus.easynats.deployment;

import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesComposeProjectBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.deployment.devservices.NatsContainer;
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
    private static final String NATS_IMAGE_NAME = "nats:latest";
    private static final String NATS_URL_PROPERTY = "quarkus.easynats.servers";
    private static final String DEVSERVICES_ENABLED_PROPERTY = "quarkus.easynats.devservices.enabled";
    public static final String CONTAINER_LABEL = "quarkus-easynats";

    private static final ContainerLocator jetStreamContainerLocator = new ContainerLocator(CONTAINER_LABEL, 4442);

    @BuildStep
    void startNatsDevService(LaunchModeBuildItem launchMode,
            DevServicesComposeProjectBuildItem composeProjectBuildItem,
            BuildProducer<DevServicesResultBuildItem> devServicesResult) {
        // Check if Dev Services are explicitly disabled
        boolean devServicesEnabled = getConfigValue(DEVSERVICES_ENABLED_PROPERTY, true);
        if (!devServicesEnabled) {
            log.info("NATS Dev Services are disabled via configuration");
            return;
        }

        // Only skip Dev Services if NATS servers are explicitly configured by the user
        // (from environment variables or system properties, not from previous Dev Services injections)
        Optional<String> explicitNatsUrl = getExplicitConfigValue(NATS_URL_PROPERTY);
        if (explicitNatsUrl.isPresent()) {
            log.infof("NATS server URL is explicitly configured to '%s', skipping Dev Services startup",
                    explicitNatsUrl.get());
            return;
        }

        log.info("Preparing NATS Dev Services container with image: " + NATS_IMAGE_NAME);

        try {
            DevServicesResultBuildItem discovered = discoverRunningService(composeProjectBuildItem, launchMode.getLaunchMode());
            if (discovered != null) {
                devServicesResult.produce(discovered);
            } else {
                devServicesResult.produce(DevServicesResultBuildItem.owned().name(FEATURE)
                        .serviceName("nats")
                        .startable(() -> {
                            try {
                                log.infof("Dev Services: Creating and starting NATS container (image: %s)", NATS_IMAGE_NAME);
                                NatsContainer container = new NatsContainer(
                                        DockerImageName.parse(NATS_IMAGE_NAME),
                                        "guest",              // Default username
                                        "guest"               // Default password
                                );
                                log.debug("Dev Services: Adding shared service label for container reuse");
                                container.withSharedServiceLabel(LaunchMode.DEVELOPMENT, FEATURE);

                                log.info("Dev Services: Starting NATS container...");
                                container.start();

                                String connectionUrl = "nats://" + container.getConnectionInfo();
                                log.infof("Dev Services: NATS container started successfully. Connection URL: %s", connectionUrl);
                                return container;
                            } catch (Exception e) {
                                log.errorf(e, "Dev Services: Failed to start NATS container");
                                throw new RuntimeException("Failed to start NATS Dev Services container", e);
                            }
                        })
                        .configProvider(Map.of(NATS_URL_PROPERTY,
                                s -> "nats://" + s.getConnectionInfo(),
                                "quarkus.easynats.username", s -> "guest",
                                "quarkus.easynats.password", s -> "guest",
                                "quarkus.easynats.ssl-enabled", s -> "false"))
                        .build());
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to prepare NATS Dev Services (error during build step): %s", e.getMessage());
            String errorMessage = String.format(
                    "Unable to start NATS Dev Services container for NATS image '%s'. " +
                    "Ensure Docker is installed and running, and the image is available. " +
                    "You can disable Dev Services by setting '%s=false' in your application.properties or environment. " +
                    "Original error: %s",
                    NATS_IMAGE_NAME, DEVSERVICES_ENABLED_PROPERTY, e.getMessage());
            throw new RuntimeException(errorMessage, e);
        }
    }

    private DevServicesResultBuildItem discoverRunningService(DevServicesComposeProjectBuildItem composeProjectBuildItem,
            LaunchMode launchMode) {
        return jetStreamContainerLocator
                .locateContainer("nats", true, launchMode)
                .or(() -> ComposeLocator.locateContainer(composeProjectBuildItem,
                        List.of("nats:latest"),
                        4222, launchMode, false))
                .map(containerAddress -> {
                    String serverUrl = "nats://" + containerAddress.getUrl();
                    return DevServicesResultBuildItem.discovered()
                            .name(FEATURE)
                            .containerId(containerAddress.getId())
                            .config(Map.of("quarkus.easynats.servers", serverUrl,
                                    "quarkus.easynats.username", "guest",
                                    "quarkus.easynats.password", "guest",
                                    "quarkus.easynats.ssl-enabled", "false"))
                            .build();
                }).orElse(null);
    }

    /**
     * Get explicit configuration value (from env vars or system properties).
     * Only checks user-provided configuration sources, NOT runtime/injected configuration.
     * This prevents Dev Services injections from the previous test run from blocking new Dev Services startup.
     */
    private Optional<String> getExplicitConfigValue(String propertyName) {
        try {
            // Check environment variables first (most explicit user config)
            // e.g. "quarkus.easynats.servers" -> "QUARKUS_EASYNATS_SERVERS"
            String envVar = propertyName.toUpperCase().replace(".", "_").replace("-", "_");
            String value = System.getenv(envVar);
            if (value != null && !value.isEmpty()) {
                log.debugf("Found explicit configuration '%s' from environment variable '%s': %s",
                        propertyName, envVar, value);
                return Optional.of(value);
            }

            // Note: We deliberately do NOT check system properties or ConfigProvider.getConfig()
            // because they can be polluted by Dev Services injections from previous builds.
            return Optional.empty();
        } catch (Exception e) {
            log.debugf(e, "Failed to read explicit configuration property: %s", propertyName);
            return Optional.empty();
        }
    }

    /**
     * Get a configuration value as a String Optional.
     * Checks explicit sources (environment variables and system properties) only,
     * NOT the runtime configuration which includes Dev Services injections.
     * This ensures Dev Services starts correctly in multi-test scenarios where
     * previous Dev Services injections should not prevent new containers from starting.
     */
    private Optional<String> getConfigValue(String propertyName) {
        try {
            // Check system properties first (highest priority)
            String value = System.getProperty(propertyName);
            if (value != null && !value.isEmpty()) {
                log.debugf("Found configuration '%s' from system property: %s", propertyName, value);
                return Optional.of(value);
            }

            // Check environment variables (convert property name to env var format)
            // e.g. "quarkus.easynats.servers" -> "QUARKUS_EASYNATS_SERVERS"
            String envVar = propertyName.toUpperCase().replace(".", "_").replace("-", "_");
            value = System.getenv(envVar);
            if (value != null && !value.isEmpty()) {
                log.debugf("Found configuration '%s' from environment variable '%s': %s",
                        propertyName, envVar, value);
                return Optional.of(value);
            }

            // Note: We deliberately do NOT check ConfigProvider.getConfig() here
            // because it includes Dev Services injections from previous builds.
            // Properties files are typically handled through system properties or env vars in tests.
            return Optional.empty();
        } catch (Exception e) {
            log.debugf(e, "Failed to read configuration property: %s", propertyName);
            return Optional.empty();
        }
    }

    /**
     * Get a configuration value as a boolean with a default.
     * Checks explicit sources (system properties and environment variables) only.
     */
    private boolean getConfigValue(String propertyName, boolean defaultValue) {
        try {
            // Check system properties first
            String value = System.getProperty(propertyName);
            if (value != null && !value.isEmpty()) {
                return Boolean.parseBoolean(value);
            }

            // Check environment variables
            String envVar = propertyName.toUpperCase().replace(".", "_").replace("-", "_");
            value = System.getenv(envVar);
            if (value != null && !value.isEmpty()) {
                return Boolean.parseBoolean(value);
            }

            return defaultValue;
        } catch (Exception e) {
            log.debugf(e, "Failed to read configuration property: %s, using default: %s",
                    propertyName, defaultValue);
            return defaultValue;
        }
    }
}
