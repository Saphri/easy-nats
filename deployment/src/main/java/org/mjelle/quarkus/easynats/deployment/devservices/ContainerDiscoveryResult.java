package org.mjelle.quarkus.easynats.deployment.devservices;

import java.util.Optional;

/**
 * Encapsulates the result of NATS container discovery from docker-compose.
 *
 * This immutable record represents the outcome of attempting to discover a running
 * NATS container from docker-compose. It provides:
 * - A status flag indicating whether discovery succeeded
 * - Extracted container configuration (if successful)
 * - A diagnostic message for logging/debugging
 *
 * <h3>Invariants</h3>
 * - If {@code found} is {@code true}, {@code containerConfig} must be present
 * - If {@code found} is {@code false}, {@code containerConfig} must be empty
 * - {@code message} is always non-empty (for logging)
 *
 * <h3>Usage Pattern</h3>
 * <pre>
 * ContainerDiscoveryResult result = discoverNatsContainer(...);
 * if (result.found()) {
 *     ContainerConfig config = result.containerConfig().orElseThrow();
 *     String connectionUrl = config.toConnectionUrl();
 * } else {
 *     log.warn(result.message());
 * }
 * </pre>
 *
 * <h3>Clustering Support</h3>
 * For NATS clustering scenarios, {@code containerConfig} contains merged configuration
 * from all discovered containers with exposed port 4222, with comma-separated values
 * in host, port, and containerId fields.
 *
 * @author Quarkus EasyNATS Extension
 * @since 1.0.0
 */
public record ContainerDiscoveryResult(
    /**
     * True if at least one NATS container was successfully discovered
     */
    boolean found,

    /**
     * Container connection details (present only if found=true)
     * Contains merged configuration from all discovered containers.
     * For clustering, connection URL list is comma-separated.
     */
    Optional<ContainerConfig> containerConfig,

    /**
     * Diagnostic message for logging/debugging
     * Populated whether discovery succeeded or failed
     */
    String message
) {
    /**
     * Convenience constructor: success case
     * @param config the discovered container configuration
     * @return discovery result with success status
     */
    public static ContainerDiscoveryResult success(ContainerConfig config) {
        return new ContainerDiscoveryResult(true, Optional.of(config),
            "NATS container(s) discovered: " + config.containerId());
    }

    /**
     * Convenience constructor: failure case
     * @param reason the reason why discovery failed
     * @return discovery result with failure status
     */
    public static ContainerDiscoveryResult notFound(String reason) {
        return new ContainerDiscoveryResult(false, Optional.empty(),
            "NATS container(s) not discovered: " + reason);
    }
}
