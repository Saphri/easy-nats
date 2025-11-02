package org.mjelle.quarkus.easynats.deployment.devservices;

import java.util.Map;

/**
 * Helper for extracting NATS credentials from container environment variables.
 *
 * This internal utility class encapsulates the logic for reading NATS configuration
 * from docker-compose container environment variables and applying sensible defaults.
 *
 * <h3>Environment Variables Supported</h3>
 * <ul>
 *   <li>{@code NATS_USERNAME}: Standard NATS username (preferred)</li>
 *   <li>{@code NATS_USER}: Alternative username variable (fallback for compatibility)</li>
 *   <li>{@code NATS_PASSWORD}: NATS password (required for auth)</li>
 *   <li>{@code NATS_TLS_CERT}: TLS certificate file path (indicates TLS enabled)</li>
 *   <li>{@code NATS_TLS_KEY}: TLS key file path (indicates TLS enabled)</li>
 *   <li>{@code NATS_TLS_CA}: TLS CA file path (indicates TLS enabled)</li>
 * </ul>
 *
 * <h3>Default Behavior</h3>
 * When environment variables are not set:
 * <ul>
 *   <li>Username defaults to "nats"</li>
 *   <li>Password defaults to "nats"</li>
 *   <li>SSL/TLS defaults to disabled (false)</li>
 * </ul>
 *
 * <h3>SSL/TLS Detection</h3>
 * TLS is considered enabled if ANY of the following conditions are met:
 * <ul>
 *   <li>{@code NATS_TLS_CERT} environment variable is set</li>
 *   <li>{@code NATS_TLS_KEY} environment variable is set</li>
 *   <li>{@code NATS_TLS_CA} environment variable is set</li>
 * </ul>
 * This approach aligns with NATS server requirements which mandate certificate
 * paths for TLS enablement.
 *
 * <h3>Usage Example</h3>
 * <pre>
 * Map&lt;String, String&gt; containerEnv = new HashMap&lt;&gt;();
 * containerEnv.put("NATS_USERNAME", "admin");
 * containerEnv.put("NATS_PASSWORD", "secretpass");
 * containerEnv.put("NATS_TLS_CERT", "/etc/nats/certs/server-cert.pem");
 *
 * CredentialExtractor.Credentials creds = CredentialExtractor.extract(containerEnv);
 * // creds.username() == "admin"
 * // creds.password() == "secretpass"
 * // creds.sslEnabled() == true
 * </pre>
 *
 * <h3>Design Notes</h3>
 * - This is an internal (package-private) utility—not part of the public API
 * - Static utility methods avoid unnecessary instantiation
 * - Immutable {@code Credentials} record ensures thread-safety
 * - No mutable state or side effects
 *
 * @author Quarkus EasyNATS Extension
 * @since 1.0.0
 * @see ContainerConfig
 * @see ContainerDiscoveryResult
 */
class CredentialExtractor {
    private static final String DEFAULT_USERNAME = "nats";
    private static final String DEFAULT_PASSWORD = "nats";
    private static final String USERNAME_ENV_VAR = "NATS_USERNAME";
    private static final String USERNAME_ALT_ENV_VAR = "NATS_USER"; // Fallback for backward compatibility
    private static final String PASSWORD_ENV_VAR = "NATS_PASSWORD";
    private static final String TLS_CERT_ENV_VAR = "NATS_TLS_CERT";
    private static final String TLS_KEY_ENV_VAR = "NATS_TLS_KEY";
    private static final String TLS_CA_ENV_VAR = "NATS_TLS_CA";

    /**
     * Represents extracted NATS credentials
     */
    public record Credentials(String username, String password, boolean sslEnabled) {}

    /**
     * Extracts NATS credentials from container environment
     * @param containerEnv map of environment variables
     * @return record with extracted username, password, ssl flag
     */
    public static Credentials extract(Map<String, String> containerEnv) {
        // Extract username - try NATS_USERNAME first, fall back to NATS_USER for backward compatibility
        String username = containerEnv.getOrDefault(USERNAME_ENV_VAR,
            containerEnv.getOrDefault(USERNAME_ALT_ENV_VAR, DEFAULT_USERNAME));

        // Extract password
        String password = containerEnv.getOrDefault(PASSWORD_ENV_VAR, DEFAULT_PASSWORD);

        // Detect TLS by checking for presence of certificate environment variables
        boolean ssl = containerEnv.containsKey(TLS_CERT_ENV_VAR)
            || containerEnv.containsKey(TLS_KEY_ENV_VAR)
            || containerEnv.containsKey(TLS_CA_ENV_VAR);

        // Fallback to label if certificate env vars not found
        if (!ssl && containerEnv.containsKey("io.nats.tls")) {
            ssl = Boolean.parseBoolean(containerEnv.get("io.nats.tls"));
        }

        return new Credentials(username, password, ssl);
    }

    // Prevent instantiation
    private CredentialExtractor() {
    }
}
