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

To test with TLS enabled:

```yaml
services:
  nats:
    image: nats:2.10-alpine
    ports:
      - "4222:4222"
    environment:
      NATS_USERNAME: secure-user
      NATS_PASSWORD: secure-pass
      NATS_TLS: "true"  # Enable TLS
```

The extension detects `NATS_TLS=true` and automatically uses `tls://` scheme.

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
