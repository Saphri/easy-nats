package org.mjelle.quarkus.easynats.core;

import java.util.concurrent.ExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.logging.Logger;
import org.mjelle.quarkus.easynats.NatsConfigurationException;
import org.mjelle.quarkus.easynats.runtime.NatsConfiguration;
import org.mjelle.quarkus.easynats.runtime.health.ConnectionStatusHolder;
import org.mjelle.quarkus.easynats.runtime.health.NatsConnectionListener;

import io.nats.client.Options;
import io.quarkus.arc.DefaultBean;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.virtual.threads.VirtualThreads;

/**
 * CDI producer for NATS connection Options.
 *
 * <p>This producer creates {@link io.nats.client.Options} instances from {@link NatsConfiguration}.
 * It uses {@link DefaultBean} annotation to allow developers to override the default Options by
 * providing their own unqualified {@link Options} bean in their application.
 *
 * <p>When a developer provides a custom {@link Options} bean, this default producer is bypassed
 * entirely, and the custom bean takes complete responsibility for configuration.
 *
 * <p><strong>Important Caveats for Custom Options Beans:</strong>
 *
 * <ul>
 *   <li><strong>@Unremovable is MANDATORY:</strong> Custom beans MUST use
 *       {@code @Produces @Unremovable} or Quarkus will optimize them away, causing startup failure
 *   <li><strong>Complete Configuration Responsibility:</strong> When custom bean exists,
 *       NatsConfiguration properties ({@code quarkus.easynats.*}) are COMPLETELY IGNORED
 *   <li><strong>Single Bean Rule:</strong> Only ONE unqualified Options bean is allowed. Multiple
 *       beans cause {@code AmbiguousResolutionException}
 *   <li><strong>No Partial Override:</strong> Cannot mix custom bean with configuration properties.
 *       Custom bean must provide ALL required options (servers, auth, TLS, etc.)
 *   <li><strong>Fail-Fast Approach:</strong> If custom bean throws exception during creation,
 *       application fails at startup (no fallback to defaults)
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>
 * // Default behavior: application.properties provides configuration
 * @Inject
 * Options options;  // Gets options from this producer
 *
 * // Custom behavior: developer provides a custom Options bean
 * @ApplicationScoped
 * public class CustomNatsOptions {
 *   @Produces @Unremovable  // CRITICAL: @Unremovable is required!
 *   public Options customOptions() {
 *     return new Options.Builder()
 *       .servers(new String[]{"nats://custom-server:4222"})
 *       .userInfo("custom-user", "custom-pass")
 *       .build();
 *   }
 * }
 * // Now the custom bean overrides this default producer
 * </pre>
 *
 * @see org.mjelle.quarkus.easynats.it.config.CustomOptionsProvider Example implementation
 */
@ApplicationScoped
public class NatsConnectionProducer {

  private static final Logger log = Logger.getLogger(NatsConnectionProducer.class);

  private final TlsConfigurationRegistry tlsRegistry;
  private final ExecutorService executorService;
  private final ConnectionStatusHolder statusHolder;

  /**
   * Constructor injection of TLS registry, executor service, and status holder.
   *
   * <p>These dependencies are needed to build a complete, ready-to-use Options instance: -
   * TlsConfigurationRegistry: Resolves TLS configurations - ExecutorService: Used for async
   * operations in NATS connection - ConnectionStatusHolder: Used for health check connection
   * listener
   *
   * @param tlsRegistry the Quarkus TLS configuration registry
   * @param executorService the virtual thread executor service for async operations
   * @param statusHolder the connection status holder for health checks
   */
  public NatsConnectionProducer(
      TlsConfigurationRegistry tlsRegistry,
      @VirtualThreads ExecutorService executorService,
      ConnectionStatusHolder statusHolder) {
    this.tlsRegistry = tlsRegistry;
    this.executorService = executorService;
    this.statusHolder = statusHolder;
  }

  /**
   * Produces a default NATS Options bean from configuration properties.
   *
   * <p>This method is annotated with {@link DefaultBean}, which means: - If no other unqualified
   * {@link Options} bean exists, this producer creates the default - If a developer provides a
   * custom {@link Options} bean, this producer is bypassed completely - CDI bean resolution handles
   * the selection automatically
   *
   * <p>The produced Options includes: - Server URLs from {@code quarkus.easynats.servers} -
   * Authentication (if configured) from {@code quarkus.easynats.username} and {@code
   * quarkus.easynats.password} - SSL/TLS settings from {@code quarkus.easynats.ssl-enabled} and
   * {@code quarkus.easynats.tls-configuration-name}
   *
   * @param config the NATS configuration containing property values
   * @return a configured {@link Options} instance ready for connection establishment
   * @throws NatsConfigurationException if required properties are missing or invalid
   */
  @Produces
  @DefaultBean
  public Options natsOptions(NatsConfiguration config) {
    log.debug("Producing default NATS Options from configuration properties");

    // Validate that required properties are present
    validateConfiguration(config);

    try {
      // Build Options from configuration
      Options.Builder optionsBuilder = new Options.Builder();

      // Set servers (validated above, guaranteed to exist)
      String[] serverUrls = config.servers().get().toArray(new String[0]);
      optionsBuilder.servers(serverUrls);
      log.debugf("Configured servers: %s", String.join(", ", serverUrls));

      // Set authentication if configured
      if (config.username().isPresent() && config.password().isPresent()) {
        optionsBuilder.userInfo(config.username().get(), config.password().get());
        log.debug("Configured user authentication");
      }

      // Add executor service for async operations
      optionsBuilder.executor(executorService);
      log.debug("Configured executor service for async operations");

      // Add connection listener for health checks
      optionsBuilder.connectionListener(new NatsConnectionListener(statusHolder));
      log.debug("Configured connection listener for health checks");

      // Set SSL/TLS if enabled
      if (config.sslEnabled()) {
        // Always set the SSLContext if a TLS configuration is available in Quarkus.
        // The jnats client will only use it if the server URL has a TLS scheme (tls://, wss://).
        var tlsConfiguration =
            config.tlsConfigurationName().flatMap(tlsRegistry::get).or(tlsRegistry::getDefault);

        if (tlsConfiguration.isPresent()) {
          try {
            optionsBuilder.sslContext(tlsConfiguration.get().createSSLContext());
            log.infof(
                "Configured TLS for NATS connection using: %s",
                config.tlsConfigurationName().orElse("default"));
          } catch (Exception e) {
            throw new NatsConfigurationException(
                "Failed to create SSLContext from TLS configuration: "
                    + config.tlsConfigurationName().orElse("default"),
                e);
          }
        } else {
          optionsBuilder.secure();
          log.debug("Configured SSL/TLS (secure mode enabled without custom TLS config)");
        }
      }

      Options options = optionsBuilder.build();
      log.debug("Successfully created default NATS Options");
      return options;

    } catch (NatsConfigurationException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg =
          String.format("Failed to create NATS Options from configuration: %s", e.getMessage());
      log.errorf(e, errorMsg);
      throw new NatsConfigurationException(errorMsg, e);
    }
  }

  /**
   * Validates that required configuration properties are present and valid.
   *
   * <p>Validation rules: - {@code quarkus.easynats.servers} must be present and non-empty - {@code
   * quarkus.easynats.username} and {@code quarkus.easynats.password} must be present together
   *
   * <p>If validation fails, a {@link NatsConfigurationException} is thrown with a clear error
   * message.
   *
   * @param config the configuration to validate
   * @throws NatsConfigurationException if any required property is missing or invalid
   */
  private void validateConfiguration(NatsConfiguration config) {
    // Validate servers property
    if (config.servers().isEmpty()) {
      throw new NatsConfigurationException(
          "quarkus.easynats.servers is required when using the default NATS Options producer. "
              + "Either configure servers in application.properties (e.g., quarkus.easynats.servers=nats://localhost:4222) "
              + "or provide a custom @Produces Options bean in your application.");
    }

    java.util.List<String> servers = config.servers().get();
    if (servers.isEmpty()) {
      throw new NatsConfigurationException(
          "quarkus.easynats.servers must not be empty. "
              + "Provide at least one NATS server URL (e.g., nats://localhost:4222).");
    }

    // Validate each server URL is non-empty
    for (String server : servers) {
      if (server == null || server.trim().isEmpty()) {
        throw new NatsConfigurationException(
            "Server URL in quarkus.easynats.servers cannot be empty or null. "
                + "Provide valid NATS server URLs (e.g., nats://localhost:4222).");
      }
    }

    // Validate username/password pairing
    boolean hasUsername = config.username().filter(s -> !s.isEmpty()).isPresent();
    boolean hasPassword = config.password().filter(s -> !s.isEmpty()).isPresent();

    if (hasUsername && !hasPassword) {
      throw new NatsConfigurationException(
          "quarkus.easynats.username is specified but quarkus.easynats.password is missing. "
              + "Either provide both username and password, or provide neither.");
    }

    if (hasPassword && !hasUsername) {
      throw new NatsConfigurationException(
          "quarkus.easynats.password is specified but quarkus.easynats.username is missing. "
              + "Either provide both username and password, or provide neither.");
    }
  }
}
