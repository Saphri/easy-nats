# Quickstart Guide: Docker Compose NATS Dev Services

**Feature**: 017-docker-compose-discovery
**Audience**: Quarkus developers using the EasyNATS extension
**Duration**: 5 minutes to get NATS running with auto-discovery

---

## What This Enables

Automatic NATS configuration through docker-compose. Instead of manually configuring NATS connection details in `application.properties`, the Quarkus extension automatically discovers your NATS container and applies the correct settings.

**Before** (without this feature):
```properties
# application.properties - manual configuration required
quarkus.easynats.servers=nats://localhost:4222
quarkus.easynats.username=admin
quarkus.easynats.password=secret
```

**After** (with this feature):
```bash
# Start docker-compose - that's it!
docker-compose -f docker-compose-devservices.yml up -d

# Application auto-discovers NATS and connects immediately
mvn quarkus:dev
```

---

## Setup Steps

### Step 1: Create `docker-compose-devservices.yml`

Place this file in your project root (or any location you prefer):

```yaml
version: '3.8'

services:
  nats:
    image: nats:2.10-alpine
    container_name: nats-dev
    ports:
      - "4222:4222"      # NATS client port
      - "6222:6222"      # NATS routing port (optional)
      - "8222:8222"      # NATS monitoring port (optional)
    environment:
      NATS_USERNAME: admin
      NATS_PASSWORD: secretpass
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "4222"]
      interval: 5s
      timeout: 2s
      retries: 3
```

**Configuration Notes**:
- `NATS_USERNAME` and `NATS_PASSWORD`: Set your credentials here
- Ports: Standard NATS ports (4222 is required, others optional)
- Image: Use any NATS official image tag (2.10, latest, alpine, etc.)

### Step 2: Start Docker Compose

```bash
# Navigate to project root
cd /path/to/your/project

# Start the NATS container in background
docker-compose -f docker-compose-devservices.yml up -d

# Verify NATS is running
docker-compose -f docker-compose-devservices.yml logs nats
```

### Step 3: Update `application.properties` (Optional)

**For development**: Leave it empty or remove NATS properties entirely. The extension auto-discovers.

```properties
# application.properties
# No NATS dev services configuration needed!
# quarkus.easynats.servers is auto-configured by dev services
```

**For production/non-dev environments**: Keep explicit configuration:

```properties
# application-prod.properties
quarkus.easynats.servers=nats://production-nats.example.com:4222
quarkus.easynats.username=prod-user
quarkus.easynats.password=prod-secret
quarkus.easynats.ssl-enabled=true
```

### Step 4: Run Your Application

```bash
# In development mode - extension auto-discovers NATS
mvn quarkus:dev

# Application starts, extension discovers NATS container, and connects automatically
# You should see logs like:
# INFO  Dev Services: NATS container discovered successfully...
# INFO  NATS connection established...
```

---

## NATS Configuration Guide

### Docker Compose Setup

The NATS service definition in docker-compose requires proper configuration for the extension to discover and extract credentials correctly.

#### Basic Configuration (No Auth)

Minimal setup for local development:

```yaml
services:
  nats:
    image: nats:2.10-alpine
    container_name: nats-dev
    ports:
      - "4222:4222"
    command: ["-js"]  # Enable JetStream
```

**What the extension discovers**:
- Host: `localhost`
- Port: `4222`
- Username: `nats` (default)
- Password: `nats` (default)
- SSL: `false` (default)

#### With Authentication

Use environment variables to configure credentials:

```yaml
services:
  nats:
    image: nats:2.10
    container_name: nats-dev
    ports:
      - "4222:4222"
      - "8222:8222"  # HTTP monitoring
    environment:
      NATS_USERNAME: admin
      NATS_PASSWORD: secretpass123
    command: ["-js", "--http_port", "8222"]
```

**What the extension discovers**:
- Host: `localhost`
- Port: `4222`
- Username: `admin` (from `NATS_USERNAME`)
- Password: `secretpass123` (from `NATS_PASSWORD`)
- SSL: `false`

#### Configuration File Approach

For more complex setups, use a NATS configuration file with environment variable substitution:

**docker-compose-devservices.yml**:
```yaml
services:
  nats:
    image: nats:2.10
    container_name: nats-dev
    ports:
      - "4222:4222"
      - "8222:8222"
    environment:
      NATS_USER: ruser
      NATS_PASSWORD: T0pS3cr3t
    volumes:
      - ./nats.conf:/etc/nats/nats.conf:ro
    command: ["-c", "/etc/nats/nats.conf", "-js"]
    labels:
      io.quarkus.devservices.compose.wait_for.logs: ".*Server is ready.*"
```

**nats.conf**:
```properties
# Listen on standard port
listen: 0.0.0.0:4222

# HTTP monitoring port
http: 8222

# Authentication using environment variables
authorization {
  users = [
    { user: $NATS_USER, password: $NATS_PASSWORD }
  ]
}

# JetStream configuration
jetstream: {
  store_dir: /tmp/nats/jetstream
}
```

**What the extension discovers**:
- Reads `NATS_USER` and `NATS_PASSWORD` from environment
- Host: `localhost`
- Port: `4222`
- Username: `ruser` (from `NATS_USER`)
- Password: `T0pS3cr3t` (from `NATS_PASSWORD`)
- SSL: `false`

**Note**: The extension looks for `NATS_USERNAME` and `NATS_PASSWORD` environment variables by default. If your config uses different names (like `NATS_USER`), you must ensure the variable names match the extension's expectations or update them.

#### With TLS/SSL

To enable TLS for secure connections, NATS requires certificate file paths via environment variables:

**docker-compose-devservices.yml** (with TLS):
```yaml
services:
  nats:
    image: nats:2.10
    container_name: nats-dev
    ports:
      - "4222:4222"
      - "8222:8222"
    environment:
      NATS_USERNAME: secure-user
      NATS_PASSWORD: secure-pass
      NATS_TLS_CERT: /etc/nats/certs/server-cert.pem
      NATS_TLS_KEY: /etc/nats/certs/server-key.pem
      NATS_TLS_CA: /etc/nats/certs/ca.pem
    volumes:
      - ./nats.conf:/etc/nats/nats.conf:ro
      - ./certs:/etc/nats/certs:ro
    command: ["-c", "/etc/nats/nats.conf", "-js"]
```

**nats.conf** (with TLS):
```properties
# Listen on standard port
listen: 0.0.0.0:4222

# HTTP monitoring port
http: 8222

# Authentication
authorization {
  users = [
    { user: $NATS_USERNAME, password: $NATS_PASSWORD }
  ]
}

# TLS Configuration (using environment variable paths)
tls: {
  cert_file: $NATS_TLS_CERT
  key_file:  $NATS_TLS_KEY
  ca_file:   $NATS_TLS_CA
}

# JetStream configuration
jetstream: {
  store_dir: /tmp/nats/jetstream
}
```

**What the extension discovers**:
- Host: `localhost`
- Port: `4222`
- Username: `secure-user` (from `NATS_USERNAME`)
- Password: `secure-pass` (from `NATS_PASSWORD`)
- SSL: `true` (detected from presence of `NATS_TLS_CERT`, `NATS_TLS_KEY`, or `NATS_TLS_CA`)
- Connection URL: `tls://localhost:4222` (automatically uses `tls://` scheme when certificates are configured)

**Certificate Setup** (for TLS):

Generate self-signed certificates for local development:

```bash
# Create certificates directory
mkdir -p certs

# Generate CA certificate
openssl genrsa -out certs/ca-key.pem 2048
openssl req -new -x509 -days 365 -key certs/ca-key.pem -out certs/ca.pem \
  -subj "/CN=nats-ca"

# Generate server key
openssl genrsa -out certs/server-key.pem 2048

# Create server certificate signing request
openssl req -new -key certs/server-key.pem -out certs/server.csr \
  -subj "/CN=nats"

# Sign server certificate with CA
openssl x509 -req -days 365 -in certs/server.csr \
  -CA certs/ca.pem -CAkey certs/ca-key.pem -CAcreateserial \
  -out certs/server-cert.pem

# Clean up CSR
rm certs/server.csr
```

Then add `certs` directory to version control or gitignore as needed.

### Environment Variables Recognized

The NATS extension recognizes these standard environment variables:

| Variable | Purpose | Example |
|----------|---------|---------|
| `NATS_USERNAME` | Client authentication username | `NATS_USERNAME=admin` |
| `NATS_PASSWORD` | Client authentication password | `NATS_PASSWORD=secret` |
| `NATS_TLS_CERT` | TLS server certificate file path | `NATS_TLS_CERT=/etc/nats/certs/server-cert.pem` |
| `NATS_TLS_KEY` | TLS server private key file path | `NATS_TLS_KEY=/etc/nats/certs/server-key.pem` |
| `NATS_TLS_CA` | TLS CA certificate file path | `NATS_TLS_CA=/etc/nats/certs/ca.pem` |
| `NATS_USER` | Alternative username variable | `NATS_USER=ruser` (see note below) |

**TLS Detection**: The extension detects TLS by checking for the presence of ANY of these certificate environment variables:
- `NATS_TLS_CERT`
- `NATS_TLS_KEY`
- `NATS_TLS_CA`

If any are set, the connection uses `tls://` scheme. If none are set, it uses `nats://` (unencrypted).

**⚠️ Important**: The extension looks for `NATS_USERNAME` and `NATS_PASSWORD` by default. If your setup uses different environment variable names (e.g., `NATS_USER` instead of `NATS_USERNAME`), you must either:
1. Rename them to `NATS_USERNAME`/`NATS_PASSWORD` in docker-compose, OR
2. Update the nats.conf to use the correct variable names that match what the extension expects

### JetStream Configuration

The extension requires JetStream to be enabled in NATS:

```properties
# In docker-compose command:
command: ["-js"]  # Simple flag to enable JetStream

# Or in nats.conf:
jetstream: {
  store_dir: /tmp/nats/jetstream
}
```

### Health Checks

Add health checks to ensure NATS is ready before the application starts:

```yaml
services:
  nats:
    image: nats:2.10
    # ... other config ...
    healthcheck:
      test: ["CMD", "nats", "server", "info"]
      interval: 5s
      timeout: 2s
      retries: 3
      start_period: 10s
    labels:
      # Alternative: Wait for specific log output
      io.quarkus.devservices.compose.wait_for.logs: ".*Server is ready.*"
```

---

## Testing Auto-Discovery

### Verify Container Is Discovered

Check the startup logs for these messages:

```
[DEBUG] Attempting to discover NATS container with image nats:* on port 4222
[INFO] Dev Services: Creating and starting NATS container...
[INFO] Dev Services: NATS container started successfully. Connection URL: nats://localhost:4222
```

### Quick Integration Test

Create a simple REST endpoint to verify connection:

```java
@Path("/health")
@Produces(MediaType.TEXT_PLAIN)
public class NatsHealthCheck {
    @Inject
    NatsConnection natsConnection;

    @GET
    public String checkConnection() {
        boolean connected = natsConnection.getStatus().isOk();
        return connected ? "NATS Connected" : "NATS Disconnected";
    }
}
```

```bash
# Test the endpoint
curl http://localhost:8080/health
# Output: NATS Connected
```

---

## Common Scenarios

### Scenario 1: Custom Port

If you want NATS on a non-standard port:

```yaml
services:
  nats:
    image: nats:2.10-alpine
    ports:
      - "4223:4222"  # Map 4223 on host to 4222 in container
    environment:
      NATS_USERNAME: dev
      NATS_PASSWORD: dev123
```

The extension automatically detects the port mapping and configures accordingly.

### Scenario 2: TLS/SSL Enabled

To test with TLS enabled, see the **With TLS/SSL** section in the [NATS Configuration Guide](#with-tlsssl) above. It includes:

- Full docker-compose configuration
- nats.conf with TLS settings
- Certificate generation commands
- What the extension discovers

Quick example:

```yaml
services:
  nats:
    image: nats:2.10
    ports:
      - "4222:4222"
    environment:
      NATS_USERNAME: secure-user
      NATS_PASSWORD: secure-pass
      NATS_TLS_CERT: /etc/nats/certs/server-cert.pem
      NATS_TLS_KEY: /etc/nats/certs/server-key.pem
      NATS_TLS_CA: /etc/nats/certs/ca.pem
    volumes:
      - ./nats.conf:/etc/nats/nats.conf:ro
      - ./certs:/etc/nats/certs:ro
    command: ["-c", "/etc/nats/nats.conf", "-js"]
```

The extension automatically detects the certificate paths and uses `tls://` scheme for the connection URL.

### Scenario 3: No Authentication (Development Only!)

For quick local testing without credentials:

```yaml
services:
  nats:
    image: nats:2.10-alpine
    ports:
      - "4222:4222"
    # No environment variables = no auth required
```

Extension defaults to "nats"/"nats" credentials (NATS default behavior).

### Scenario 4: Stopping and Restarting

```bash
# Stop containers but keep data
docker-compose -f docker-compose-devservices.yml stop

# Restart (extension auto-rediscovers)
docker-compose -f docker-compose-devservices.yml start

# Completely remove (fresh start)
docker-compose -f docker-compose-devservices.yml down
```

After stopping/restarting, the extension automatically rediscovers the container on next application startup.

---

## Troubleshooting

### Issue: "NATS container not discovered"

**Causes & Solutions**:

1. **docker-compose not running**
   ```bash
   # Check if containers are running
   docker-compose -f docker-compose-devservices.yml ps

   # Start if not running
   docker-compose -f docker-compose-devservices.yml up -d
   ```

2. **Container image mismatch**
   ```bash
   # Verify image name matches (default: nats:*)
   docker-compose -f docker-compose-devservices.yml ps
   docker image ls | grep nats
   ```

3. **Port conflicts**
   ```bash
   # Check if port 4222 is already in use
   lsof -i :4222  # macOS/Linux
   netstat -ano | findstr :4222  # Windows
   ```

### Issue: "Connection refused" after discovery

**Likely Causes**:
- Container is starting but not ready yet
- Credentials don't match docker-compose environment variables
- Network issue between host and container

**Solutions**:
```bash
# Check container logs
docker-compose -f docker-compose-devservices.yml logs nats

# Verify credentials match
docker-compose -f docker-compose-devservices.yml exec nats env | grep NATS

# Test connectivity directly
docker-compose -f docker-compose-devservices.yml exec nats \
  nats server info
```

### Issue: Application starts but NATS connection fails

**Check**:
1. Are the extracted credentials correct?
   ```bash
   # View Quarkus dev services logs
   # Extension logs show extracted username/password (at DEBUG level)
   mvn quarkus:dev -Dquarkus.log.level=DEBUG
   ```

2. Is docker-compose running in the same network?
   ```bash
   docker-compose -f docker-compose-devservices.yml ps
   ```

---

## Configuration Reference

### Docker Compose Environment Variables

The extension recognizes these NATS environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `NATS_USERNAME` | "nats" | Authentication username |
| `NATS_PASSWORD` | "nats" | Authentication password |
| `NATS_TLS` | "false" | Enable TLS/SSL |
| `NATS_PORT` | 4222 | Client port (usually mapped in docker-compose) |

### Quarkus Dev Services Properties

These are automatically configured from the discovered container:

| Property | Source |
|----------|--------|
| `quarkus.easynats.servers` | Docker container host:port |
| `quarkus.easynats.username` | `NATS_USERNAME` env var |
| `quarkus.easynats.password` | `NATS_PASSWORD` env var |
| `quarkus.easynats.ssl-enabled` | `NATS_TLS` env var |

**Note**: Do not manually set these properties for dev services. Set them only in `application-prod.properties` for non-dev environments.

---

## Best Practices

### ✅ Do

- **Store docker-compose file in version control** (without sensitive data)
- **Use environment variables for credentials** in docker-compose
- **Verify container is running** before starting the application
- **Check logs** when discovery issues occur
- **Use unique container names** to avoid conflicts

### ❌ Don't

- **Hardcode credentials** in docker-compose (use environment variables)
- **Run multiple NATS containers** for the same application (confusion)
- **Mix property-based and discovery-based configuration** (discovery takes precedence)
- **Manually set `quarkus.easynats.*` properties** in dev mode (dev services will override)
- **Rely on container auto-cleanup** - explicitly stop/remove when done

---

## Reference Configuration Files

This project includes example NATS configuration files in the integration-tests directory:

### Example: Project's Integration Test Setup

**File**: `integration-tests/docker-compose-devservices.yml`

Shows a production-like setup with:
- Configuration file mounting
- Environment variable substitution
- Multiple services (NATS + observability stack)
- Health check and readiness configuration

```yaml
name: easy-nats-integration-tests
services:
  nats:
    image: nats:2.11
    container_name: nats
    environment:
      NATS_USER: ruser
      NATS_PASSWORD: T0pS3cr3t
    ports:
      - "4222:4222"
      - "8222:8222"
    volumes:
      - ./nats.conf:/etc/nats/nats.conf:ro
    command: ["-c", "/etc/nats/nats.conf", "-js"]
    labels:
      io.quarkus.devservices.compose.wait_for.logs: ".*Server is ready.*"
```

**File**: `integration-tests/nats.conf`

Configuration file supporting:
- Environment variable substitution
- JetStream setup
- TLS configuration (commented out, ready to enable)

```properties
listen: 0.0.0.0:4222
http: 8222

authorization {
  users = [
    { user: $NATS_USER, password: $NATS_PASSWORD }
  ]
}

jetstream: {
  store_dir: /tmp/nats/jetstream
}

# TLS can be enabled by uncommenting:
# tls: {
#   cert_file: /etc/nats/certs/server-cert.pem
#   key_file:  /etc/nats/certs/server-key.pem
#   ca_file:   /etc/nats/certs/ca.pem
# }
```

**Note on Environment Variables**: This setup uses `NATS_USER` and `NATS_PASSWORD` instead of the standard `NATS_USERNAME` and `NATS_PASSWORD`. The extension looks for the standard names by default. For this configuration to work with the extension:

**Option 1** (Recommended): Update docker-compose to use standard variable names:
```yaml
environment:
  NATS_USERNAME: ruser      # Changed from NATS_USER
  NATS_PASSWORD: T0pS3cr3t  # Keep as-is
```

And update nats.conf:
```properties
authorization {
  users = [
    { user: $NATS_USERNAME, password: $NATS_PASSWORD }  # Updated names
  ]
}
```

**Option 2**: Update the extension to recognize `NATS_USER` (requires code change in discovery logic)

---

## Testing Coverage

The feature implementation is validated by the **existing integration test suite**:

- Tests run against docker-compose NATS service
- If container discovery or configuration fails, tests will fail
- Provides end-to-end validation of the feature
- No additional test scenarios needed beyond current suite
- Native image tests (`@QuarkusIntegrationTest`) inherit coverage from JVM tests

This means:
1. ✅ Feature correctness validated by existing tests
2. ✅ Configuration extraction verified through actual connection
3. ✅ JVM and native image compatibility covered
4. ✅ No special test setup required beyond current docker-compose

---

## Next Steps

1. **Review the specification** for detailed behavior: [spec.md](spec.md)
2. **Check the data model** for technical details: [data-model.md](data-model.md)
3. **Read the processor contract** for implementation specifics: [contracts/build-processor-contract.md](contracts/build-processor-contract.md)

---

## Support

If you encounter issues:

1. Check the troubleshooting section above
2. Enable DEBUG logging: `mvn quarkus:dev -Dquarkus.log.level=DEBUG`
3. Review docker-compose and container configuration
4. Check GitHub issues for similar problems

---
