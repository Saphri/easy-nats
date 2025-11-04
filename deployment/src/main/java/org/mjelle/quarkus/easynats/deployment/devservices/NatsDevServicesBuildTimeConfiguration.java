package org.mjelle.quarkus.easynats.deployment.devservices;

import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Quarkus Dev Services configuration for NATS.
 *
 * <p>Configuration properties under {@code quarkus.easynats.devservices.*} are mapped to this
 * class. This configuration is used at build time to control NATS container provisioning and
 * initialization.
 *
 * <p>Example configuration in application.properties:
 *
 * <pre>
 * quarkus.easynats.devservices.enabled=true
 * quarkus.easynats.devservices.image-name=nats:latest
 * quarkus.easynats.devservices.username=guest
 * quarkus.easynats.devservices.password=guest
 * quarkus.easynats.devservices.port=4222
 * quarkus.easynats.devservices.ssl-enabled=false
 * quarkus.easynats.devservices.shared=true
 * quarkus.easynats.devservices.service-name=nats
 * </pre>
 */
@ConfigMapping(prefix = "quarkus.easynats.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface NatsDevServicesBuildTimeConfiguration {

  /**
   * Whether Dev Services for NATS are enabled.
   *
   * <p>When disabled, no automatic NATS container will be started. You must provide explicit server
   * configuration via {@code quarkus.easynats.servers}.
   */
  @WithDefault("true")
  boolean enabled();

  /**
   * The Docker image name to use for the NATS container.
   *
   * <p>Examples: "nats:latest", "nats:2.10.0-alpine", "nats:alpine"
   */
  @WithDefault("nats:2.11")
  String imageName();

  /**
   * Optional fixed port the dev service will listen to.
   *
   * <p>If not defined, the port will be chosen randomly.
   */
  OptionalInt port();

  /**
   * Whether to use a shared NATS container across multiple tests.
   *
   * <p>When true, Dev Services will reuse the same container across test runs in development mode.
   * When false, a new container is started for each application startup.
   */
  @WithDefault("true")
  boolean shared();

  /**
   * The service name for the shared NATS container.
   *
   * <p>This name is used as a label to identify the container in Docker for reuse.
   */
  @WithDefault("nats")
  String serviceName();

  /**
   * The username for NATS authentication.
   *
   * <p>Used when starting the NATS container and injected into application configuration.
   */
  @WithDefault("guest")
  String username();

  /**
   * The password for NATS authentication.
   *
   * <p>Used when starting the NATS container and injected into application configuration.
   */
  @WithDefault("guest")
  String password();

  /**
   * Whether to enable TLS/SSL for the NATS container.
   *
   * <p>When enabled, connection URLs use the {@code tls://} scheme instead of {@code nats://}.
   */
  @WithDefault("false")
  boolean sslEnabled();
}
