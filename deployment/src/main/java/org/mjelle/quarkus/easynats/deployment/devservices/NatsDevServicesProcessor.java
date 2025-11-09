package org.mjelle.quarkus.easynats.deployment.devservices;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

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

/**
 * Quarkus Dev Services processor for NATS with docker-compose discovery.
 *
 * <h3>Overview</h3>
 *
 * This build-time processor discovers running NATS containers from docker-compose and automatically
 * configures the application to connect to them. It operates in discovery-only mode: it does NOT
 * create or manage NATS containers—it only discovers pre-existing ones.
 *
 * <h3>How It Works</h3>
 *
 * <ol>
 *   <li>During Quarkus build-time (dev/test mode), checks if Dev Services are enabled
 *   <li>Attempts to discover NATS containers from running docker-compose services
 *   <li>For each discovered container, extracts credentials from environment variables
 *   <li>Builds and produces Quarkus configuration mapping discovered container details
 *   <li>If no container found, logs warning and continues (application must configure manually)
 * </ol>
 *
 * <h3>Discovery Flow</h3>
 *
 * <pre>
 * [Docker-Compose Service] (nats container with env vars)
 *         |
 *         | (detect & extract)
 *         |
 * [ContainerAddress] (from ComposeLocator)
 *         |
 *         | (extract env vars via RunningContainer.tryGetEnv())
 *         |
 * [ContainerDiscoveryResult] (success with extracted config)
 *         |
 *         | (apply to Quarkus properties)
 *         |
 * [DevServicesResultBuildItem.discovered()] -> Quarkus configuration
 * </pre>
 *
 * <h3>Credential Extraction</h3>
 *
 * The processor uses {@link CredentialExtractor} to read NATS credentials from container
 * environment variables: - {@code NATS_USERNAME} or {@code NATS_USER} (defaults to "nats") - {@code
 * NATS_PASSWORD} (defaults to "nats") - Detects TLS from {@code NATS_TLS_CERT}, {@code
 * NATS_TLS_KEY}, {@code NATS_TLS_CA}
 *
 * <h3>No Fallback Behavior</h3>
 *
 * This is a critical design feature: The processor does NOT:
 *
 * <ul>
 *   <li>Create or start managed containers (unlike older Quarkus extensions)
 *   <li>Fall back to application.properties configuration values
 *   <li>Use default/hardcoded credentials as fallback
 * </ul>
 *
 * If docker-compose discovery fails, the application must have explicit NATS server configuration
 * (via {@code quarkus.easynats.servers} property) to function.
 *
 * <h3>Clustering Support</h3>
 *
 * For NATS clustering scenarios, the processor can discover all containers with exposed port 4222.
 * The {@link ContainerConfig} record supports comma-separated values for multi-node clusters,
 * generating connection URL lists automatically.
 *
 * <h3>Dependency Injection</h3>
 *
 * Uses Quarkus dev services framework: - {@code LaunchModeBuildItem}: Checks build mode (only
 * DEV/TEST) - {@code DevServicesComposeProjectBuildItem}: Docker-compose project context - {@code
 * DevServicesConfig}: Framework-level enable/disable - {@code
 * BuildProducer<DevServicesResultBuildItem>}: Produces configuration
 *
 * <h3>Configuration Properties</h3>
 *
 * When discovery succeeds, the processor produces configuration for: - {@code
 * quarkus.easynats.servers}: Connection URL(s) from discovered container(s) - {@code
 * quarkus.easynats.username}: Extracted username from environment - {@code
 * quarkus.easynats.password}: Extracted password from environment - {@code
 * quarkus.easynats.ssl-enabled}: TLS flag from certificate env vars
 *
 * @author Quarkus EasyNATS Extension
 * @since 1.0.0
 * @see ContainerDiscoveryResult
 * @see ContainerConfig
 * @see CredentialExtractor
 * @see NatsDevServicesBuildTimeConfiguration
 */
@BuildSteps(onlyIf = {IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class})
public class NatsDevServicesProcessor {

  private static final Logger log = Logger.getLogger(NatsDevServicesProcessor.class);
  private static final String FEATURE = "quarkus-easynats-devservices";
  private static final int NATS_PORT = 4222;

  private static final ContainerLocator natsContainerLocator =
      new ContainerLocator("nats", NATS_PORT);

  @BuildStep
  void startNatsDevService(
      LaunchModeBuildItem launchMode,
      DevServicesComposeProjectBuildItem composeProjectBuildItem,
      NatsDevServicesBuildTimeConfiguration config,
      DevServicesConfig devServicesConfig,
      List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
      BuildProducer<DevServicesResultBuildItem> devServicesResult) {
    // Check if Dev Services are explicitly disabled
    if (!config.enabled()) {
      log.debug("NATS Dev Services are disabled via configuration");
      return;
    }

    ContainerDiscoveryResult discoveryResult = null;
    try {
      discoveryResult =
          discoverNatsContainer(composeProjectBuildItem, launchMode.getLaunchMode(), config);

      if (discoveryResult.found()) {
        ContainerConfig containerConfig = discoveryResult.containerConfig().orElseThrow();
        log.info(discoveryResult.message());
        log.infof(
            "Dev Services: NATS discovered at %s with connection URL: %s",
            containerConfig.host(), containerConfig.toConnectionUrl());

        // Produce discovered container as dev services result
        devServicesResult.produce(
            DevServicesResultBuildItem.discovered()
                .name(FEATURE)
                .containerId(containerConfig.containerId())
                .config(containerConfig.toConfigurationMap())
                .build());
      } else {
        log.warn(
            discoveryResult.message()
                + ". NATS Dev Services not initialized. "
                + "Ensure docker-compose NATS service is running or configure quarkus.easynats.servers explicitly.");
      }
    } catch (Exception e) {
      if (discoveryResult == null) {
        // This indicates an exception was thrown during the discovery process itself.
        log.errorf(e, "NATS container discovery failed with an unexpected error");
        throw new RuntimeException("DevServices NATS discovery failed", e);
      } else {
        // This indicates discovery was successful, but processing the configuration failed.
        log.errorf(e, "NATS container discovery succeeded but processing the configuration failed");
        throw new RuntimeException("DevServices NATS configuration failed", e);
      }
    }
  }

  /**
   * Discovers running NATS containers from docker-compose project.
   *
   * @param composeProjectBuildItem the docker-compose project build item
   * @param launchMode the Quarkus launch mode
   * @param config the dev services configuration
   * @return discovery result with container info if found
   */
  private ContainerDiscoveryResult discoverNatsContainer(
      DevServicesComposeProjectBuildItem composeProjectBuildItem,
      LaunchMode launchMode,
      NatsDevServicesBuildTimeConfiguration config) {

    int port = config.port().orElse(NATS_PORT);
    log.debugf(
        "Attempting NATS container discovery with image: %s on port %d", config.imageName(), port);

    // Try to discover running container by label first, then by compose project
    Optional<ContainerDiscoveryResult> discoveryResult =
        natsContainerLocator
            .locateContainer("nats", true, launchMode)
            .or(
                () ->
                    ComposeLocator.locateContainer(
                        composeProjectBuildItem,
                        List.of(config.imageName()),
                        port,
                        launchMode,
                        false))
            .map(
                containerAddress -> {
                  log.debugf(
                      "Container found: %s at %s",
                      containerAddress.getId(), containerAddress.getUrl());

                  // Extract credentials from container environment
                  // Collect all environment variables that CredentialExtractor might need
                  Map<String, String> containerEnv = new HashMap<>();
                  var runningContainer = containerAddress.getRunningContainer();
                  if (runningContainer != null) {
                    // Collect NATS credentials and TLS vars - CredentialExtractor handles fallback
                    // logic
                    Stream.of(
                            "NATS_USERNAME",
                            "NATS_USER",
                            "NATS_PASSWORD",
                            "NATS_TLS_CERT",
                            "NATS_TLS_KEY",
                            "NATS_TLS_CA")
                        .forEach(
                            envVar ->
                                runningContainer
                                    .tryGetEnv(envVar)
                                    .ifPresent(val -> containerEnv.put(envVar, val)));
                  }

                  // Delegate all credential extraction logic to CredentialExtractor
                  CredentialExtractor.Credentials creds = CredentialExtractor.extract(containerEnv);
                  log.debugf(
                      "Extracted credentials: username=%s, ssl=%b",
                      creds.username(), creds.sslEnabled());

                  // Build container config with extracted values
                  ContainerConfig containerConfig =
                      new ContainerConfig(
                          containerAddress.getId(),
                          containerAddress.getHost(),
                          String.valueOf(containerAddress.getPort()),
                          creds.username(),
                          creds.password(),
                          creds.sslEnabled());

                  log.debug("NATS container discovery complete");
                  return ContainerDiscoveryResult.success(containerConfig);
                });

    return discoveryResult.orElseGet(
        () ->
            ContainerDiscoveryResult.notFound("No running NATS container found in docker-compose"));
  }
}
