package org.mjelle.quarkus.easynats.runtime;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import org.mjelle.quarkus.easynats.NatsConfigurationException;

import java.util.List;
import java.util.Optional;

/**
 * Configuration properties for NATS connection.
 * Prefix: quarkus.easynats
 * <p>
 * Example configuration in application.properties:
 * <pre>
 * quarkus.easynats.servers=nats://localhost:4222
 * quarkus.easynats.username=guest
 * quarkus.easynats.password=guest
 * quarkus.easynats.ssl-enabled=false
 * </pre>
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.easynats")
public interface NatsConfiguration {

    /**
     * List of NATS server URLs to connect to.
     * At least one server must be specified.
     * <p>
     * Example: nats://localhost:4222
     * Multiple servers can be specified for failover.
     *
     * @return list of server URLs
     */
    List<String> servers();

    /**
     * Username for authentication.
     * If specified, password must also be specified.
     *
     * @return optional username
     */
    Optional<String> username();

    /**
     * Password for authentication.
     * If specified, username must also be specified.
     *
     * @return optional password
     */
    Optional<String> password();

    /**
     * Name of the TLS configuration to use from Quarkus TLS registry.
     * If not specified, the default TLS configuration will be used if available.
     * <p>
     * The NATS client will only use TLS if the server URL scheme is tls:// or wss://.
     * This configuration makes the SSLContext available to the client, but doesn't
     * force TLS usage - that's determined by the server URL scheme.
     * <p>
     * Example: quarkus.easynats.tls-configuration-name=my-nats-tls
     *
     * @return optional TLS configuration name
     */
    Optional<String> tlsConfigurationName();

    /**
     * Whether to include message payloads in error logs.
     * <p>
     * When enabled (default), deserialization errors will include a truncated
     * preview of the message payload to help with debugging.
     * <p>
     * In production environments, you should disable this to prevent sensitive
     * data (PII, credentials, etc.) from appearing in logs.
     * <p>
     * Default: true (enabled for development convenience)
     * <p>
     * Example: quarkus.easynats.log-payloads-on-error=false
     *
     * @return true if payloads should be logged in error messages
     */
    @WithDefault("true")
    boolean logPayloadsOnError();

    /**
     * Validates the configuration and throws NatsConfigurationException if invalid.
     * <p>
     * Validation rules:
     * - At least one server must be specified
     * - If username is specified, password must also be specified
     * - If password is specified, username must also be specified
     *
     * @throws NatsConfigurationException if configuration is invalid
     */
    default void validate() {
        if (servers() == null || servers().isEmpty()) {
            throw new NatsConfigurationException(
                    "At least one NATS server must be configured. " +
                            "Set 'quarkus.easynats.servers' property in application.properties"
            );
        }

        // Check for empty strings in servers list
        for (String server : servers()) {
            if (server == null || server.trim().isEmpty()) {
                throw new NatsConfigurationException(
                        "NATS server URL cannot be empty. " +
                                "Check 'quarkus.easynats.servers' property in application.properties"
                );
            }
        }

        boolean hasUsername = username().isPresent() && !username().get().isEmpty();
        boolean hasPassword = password().isPresent() && !password().get().isEmpty();

        if (hasUsername && !hasPassword) {
            throw new NatsConfigurationException(
                    "Username specified but password is missing. " +
                            "Set 'quarkus.easynats.password' property in application.properties"
            );
        }

        if (hasPassword && !hasUsername) {
            throw new NatsConfigurationException(
                    "Password specified but username is missing. " +
                            "Set 'quarkus.easynats.username' property in application.properties"
            );
        }
    }
}
