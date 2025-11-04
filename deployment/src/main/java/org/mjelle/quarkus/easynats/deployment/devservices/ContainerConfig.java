package org.mjelle.quarkus.easynats.deployment.devservices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Connection configuration extracted from discovered docker-compose NATS container(s).
 *
 * <p>This immutable record encapsulates all connection metadata needed to establish a NATS client
 * connection. It is extracted from running docker-compose NATS containers via environment variable
 * inspection and Docker API access.
 *
 * <h3>Single Container Mode</h3>
 *
 * For typical development scenarios with a single NATS container:
 *
 * <pre>
 * ContainerConfig config = new ContainerConfig(
 *     "abc123def456",              // containerId (Docker container hash)
 *     "localhost",                 // host
 *     "4222",                      // port (standard NATS client port)
 *     "nats",                      // username (from NATS_USERNAME env var)
 *     "nats",                      // password (from NATS_PASSWORD env var)
 *     false                        // sslEnabled (no TLS certificates)
 * );
 * String url = config.toConnectionUrl();  // "nats://localhost:4222"
 * </pre>
 *
 * <h3>Clustering Mode</h3>
 *
 * For advanced scenarios with multiple NATS containers (clustering):
 *
 * <pre>
 * ContainerConfig config = new ContainerConfig(
 *     "abc123,def456,ghi789",                    // comma-separated container IDs
 *     "localhost,localhost,localhost",           // comma-separated hosts
 *     "4222,4223,4224",                          // comma-separated ports
 *     "nats",                                    // shared username
 *     "nats",                                    // shared password
 *     false                                      // shared SSL setting
 * );
 * String url = config.toConnectionUrl();
 * // "nats://localhost:4222,nats://localhost:4223,nats://localhost:4224"
 * </pre>
 *
 * <h3>Validation Rules</h3>
 *
 * - {@code host}: Non-empty, valid hostname or IP address - {@code port}: Each port value must be
 * numeric and in range 1-65535 - {@code username}: Non-empty string (default "nats") - {@code
 * password}: Non-null (empty string allowed per NATS spec) - {@code containerId}: Non-empty Docker
 * container identifier(s) - {@code sslEnabled}: Boolean flag for TLS enablement
 *
 * <h3>Immutability</h3>
 *
 * All fields are immutable. Use constructor for creation. The record pattern ensures thread-safety
 * and prevents accidental modification.
 *
 * <h3>Integration with Quarkus</h3>
 *
 * The {@link #toConfigurationMap()} method generates Quarkus dev services configuration that is
 * automatically applied at build time.
 *
 * @author Quarkus EasyNATS Extension
 * @since 1.0.0
 * @see ContainerDiscoveryResult
 * @see CredentialExtractor
 */
public record ContainerConfig(
    /**
     * Container ID(s) from Docker API For single container: hex string uniquely identifying the
     * container For clustering: comma-separated list of container IDs
     */
    String containerId,

    /**
     * Hostname or IP address for NATS client connection For single container: single host address
     * For clustering: comma-separated list of all discovered hosts
     */
    String host,

    /**
     * Exposed NATS client port (4222 standard, or custom if remapped) For single container: single
     * port For clustering: comma-separated list of all discovered ports
     */
    String port,

    /**
     * NATS authentication username (extracted from NATS_USERNAME env var) Default: "nats" if not
     * specified Cannot be null or empty
     */
    String username,

    /**
     * NATS authentication password (extracted from NATS_PASSWORD env var) Default: "nats" if not
     * specified Cannot be null
     */
    String password,

    /**
     * Flag indicating SSL/TLS enablement (from NATS_TLS_CERT, NATS_TLS_KEY, NATS_TLS_CA env vars)
     * TLS is enabled if ANY certificate path environment variable is present Determines scheme:
     * "tls://" if true, "nats://" if false
     */
    boolean sslEnabled) {
  // Compact constructor for validation
  public ContainerConfig {
    if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException("host cannot be null or empty");
    }
    if (port == null || port.isEmpty()) {
      throw new IllegalArgumentException("port cannot be null or empty");
    }
    // Validate each port in the list
    for (String p : port.split(",")) {
      try {
        int portNum = Integer.parseInt(p.trim());
        if (portNum < 1 || portNum > 65535) {
          throw new IllegalArgumentException("port must be 1-65535, got " + portNum);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("port must be numeric, got " + p);
      }
    }
    if (username == null || username.isEmpty()) {
      throw new IllegalArgumentException("username cannot be null or empty");
    }
    if (password == null) {
      throw new IllegalArgumentException("password cannot be null");
    }
    if (containerId == null || containerId.isEmpty()) {
      throw new IllegalArgumentException("containerId cannot be null or empty");
    }
  }

  /**
   * Generates NATS connection URL(s) from container config. For single container: returns single
   * URL (e.g., "nats://host:port") For clustering: returns comma-separated list of all discovered
   * nodes. For clustering, the number of hosts and ports MUST match.
   *
   * @return properly formatted NATS connection URL or comma-separated URL list
   * @throws IllegalArgumentException if hosts and ports counts don't match in clustering scenario
   */
  public String toConnectionUrl() {
    String scheme = sslEnabled ? "tls" : "nats";
    String[] hosts = host.split(",");
    String[] ports = port.split(",");

    // For clustering (multiple hosts), validate that hosts and ports match
    if (hosts.length > 1 && hosts.length != ports.length) {
      throw new IllegalArgumentException(
          String.format(
              "Clustering configuration mismatch: %d hosts but %d ports. "
                  + "For clustering, each host must have a corresponding port.",
              hosts.length, ports.length));
    }

    List<String> urls = new ArrayList<>();
    for (int i = 0; i < hosts.length; i++) {
      String hostPart = hosts[i].trim();
      String portPart = ports[i].trim();
      urls.add(scheme + "://" + hostPart + ":" + portPart);
    }
    return String.join(",", urls);
  }

  /**
   * Generates configuration map for Quarkus Dev Services. Supports both single container and
   * clustering scenarios. For clustering, comma-separated URL list is generated from all discovered
   * containers.
   *
   * @return property name -> value mapping
   */
  public Map<String, String> toConfigurationMap() {
    return Map.of(
        "quarkus.easynats.servers",
        toConnectionUrl(),
        "quarkus.easynats.username",
        username,
        "quarkus.easynats.password",
        password,
        "quarkus.easynats.ssl-enabled",
        String.valueOf(sslEnabled));
  }
}
