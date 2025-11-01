# Quarkus Dev Services for NATS - Developer Guide

Quarkus Dev Services automatically provisions a NATS JetStream container during development and testing, eliminating the need for manual setup. With zero configuration, you get a fully functional NATS broker running in Docker.

## Table of Contents

- [Overview](#overview)
- [Quick Start (Zero Config)](#quick-start-zero-config)
- [Configuration](#configuration)
- [Examples](#examples)
- [Docker Integration](#docker-integration)
- [Troubleshooting](#troubleshooting)

## Overview

### What are Dev Services?

Dev Services are Quarkus's built-in feature for automatically starting and managing containerized services during development and testing. For NATS, this means:

✅ **Zero Configuration** - Works out of the box
✅ **Automatic Provisioning** - Container starts when you run your app
✅ **Container Reuse** - Same container reused across test runs
✅ **Automatic Cleanup** - Container stops when your app shuts down
✅ **Port Mapping** - Automatically maps NATS ports dynamically
✅ **Network Integration** - Seamless Docker network support

### When Dev Services Start

Dev Services automatically provision NATS when:

1. **No explicit server is configured** - Neither `quarkus.easynats.servers` in properties nor environment variables
2. **Dev Services are enabled** - Default behavior (can be disabled)
3. **Running in dev/test mode** - Not in production

### When Dev Services Are Skipped

Dev Services are **NOT** used when:

1. **Explicit server is configured** - Via environment variable or properties file
2. **Dev Services are explicitly disabled** - `quarkus.easynats.devservices.enabled=false`
3. **Running in production** - JVM native image builds

## Quick Start (Zero Config)

The absolute simplest way to use EasyNATS:

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.mjelle.quarkus.easynats</groupId>
    <artifactId>quarkus-easy-nats</artifactId>
    <version>999-SNAPSHOT</version>
</dependency>
```

### 2. Define Your Message Type

```java
public record OrderEvent(
    String orderId,
    String customerId,
    double totalPrice
) {}
```

### 3. Create a Publisher

```java
@RestController
@RequestMapping("/orders")
public class OrderResource {

    private final NatsPublisher publisher;

    public OrderResource(NatsPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public void publishOrder(@RequestBody OrderEvent order) throws Exception {
        publisher.publish("orders.created", order);
    }
}
```

### 4. Create a Subscriber

```java
@Component
public class OrderListener {

    @NatsSubscriber(subject = "orders.created")
    public void onOrderCreated(OrderEvent event) {
        System.out.println("New order: " + event.orderId());
    }
}
```

### 5. Run Your App

```bash
./mvnw quarkus:dev
```

That's it! Quarkus Dev Services will:
- Start a NATS container automatically
- Inject the connection URL automatically
- Initialize your subscribers automatically
- Clean up when you stop the app

No configuration files. No manual Docker commands. No environment variables to set.

## Configuration

All Dev Services configuration properties start with `quarkus.easynats.devservices`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable Dev Services for NATS |
| `image-name` | string | `nats:2.11` | Docker image to use |
| `port` | integer (optional) | random | Fixed host port (random if not set) |
| `shared` | boolean | `true` | Use shared Docker network |
| `service-name` | string | `nats` | Container label for reuse identification |
| `username` | string | `guest` | NATS authentication username |
| `password` | string | `guest` | NATS authentication password |
| `ssl-enabled` | boolean | `false` | Enable TLS for container (future use) |

### Property Files

Set properties in `application.properties`:

```properties
# Disable Dev Services (use explicit config instead)
quarkus.easynats.devservices.enabled=false

# Use a specific NATS version
quarkus.easynats.devservices.image-name=nats:2.10.0-alpine

# Map to fixed port
quarkus.easynats.devservices.port=4222

# Custom credentials
quarkus.easynats.devservices.username=myuser
quarkus.easynats.devservices.password=mypassword

# Disable container reuse
quarkus.easynats.devservices.shared=false
```

### Environment Variables

Set via environment variables (takes precedence over properties):

```bash
export QUARKUS_EASYNATS_DEVSERVICES_ENABLED=true
export QUARKUS_EASYNATS_DEVSERVICES_IMAGE_NAME=nats:2.11
export QUARKUS_EASYNATS_DEVSERVICES_PORT=4222
export QUARKUS_EASYNATS_DEVSERVICES_USERNAME=guest
export QUARKUS_EASYNATS_DEVSERVICES_PASSWORD=guest
```

### System Properties

Pass as Java system properties:

```bash
./mvnw quarkus:dev \
  -Dquarkus.easynats.devservices.port=4222 \
  -Dquarkus.easynats.devservices.image-name=nats:alpine
```

## Examples

### Example 1: Default Configuration

No configuration needed. Everything works out of the box:

```properties
# application.properties is empty or doesn't exist
```

**Behavior**:
- NATS container starts with image `nats:2.11`
- Dynamic port assignment (e.g., `localhost:54321`)
- Credentials: `guest/guest`
- Container is reused across dev runs

### Example 2: Fixed Port

Use a known port for local testing:

```properties
# application.properties
quarkus.easynats.devservices.port=4222
```

**Behavior**:
- NATS container starts on port `4222`
- Can connect via `nats://localhost:4222`
- Fixed port enables Docker Compose integration

### Example 3: Custom NATS Version

Use a different NATS version:

```properties
# application.properties
quarkus.easynats.devservices.image-name=nats:2.10.0-alpine
quarkus.easynats.devservices.port=4222
```

**Behavior**:
- Uses Alpine Linux image (smaller footprint)
- Specific NATS version for compatibility testing
- Fixed port for local development

### Example 4: Custom Credentials

Set custom authentication:

```properties
# application.properties
quarkus.easynats.devservices.username=devuser
quarkus.easynats.devservices.password=devpassword
quarkus.easynats.devservices.port=4222
```

**Application Code** (optional):

```java
@NatsPublisher
public class MyPublisher {
    private final NatsPublisher publisher;

    public MyPublisher(NatsPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(String subject, Object message) throws Exception {
        // Credentials are automatically provided by Dev Services
        publisher.publish(subject, message);
    }
}
```

### Example 5: Disable Dev Services (Use External NATS)

When using an external NATS server:

```bash
# Set explicit server via environment variable
export QUARKUS_EASYNATS_SERVERS=nats://my-nats-server:4222

# Run your app
./mvnw quarkus:dev
```

**Behavior**:
- Dev Services are skipped
- Your explicit server is used
- No Docker container starts

Or in properties:

```properties
# application.properties
quarkus.easynats.servers=nats://nats.example.com:4222
quarkus.easynats.devservices.enabled=false
```

### Example 6: Docker Compose Integration

Use Dev Services with Docker Compose:

```yaml
# docker-compose.yml
version: '3'
services:
  app:
    build: .
    environment:
      QUARKUS_EASYNATS_DEVSERVICES_PORT: 4222
      QUARKUS_EASYNATS_DEVSERVICES_SHARED: 'true'
    ports:
      - "8080:8080"
      - "4222:4222"
```

Run with:

```bash
docker-compose up
```

## Docker Integration

### Container Reuse

By default, the NATS container is reused across development runs:

```properties
# application.properties
quarkus.easynats.devservices.shared=true  # Reuse container
quarkus.easynats.devservices.shared=false # New container each run
```

**Benefits of Reuse**:
- ✅ Faster startup (container already exists)
- ✅ Persistent data (messages retained across runs)
- ✅ Same port (predictable for testing)
- ✅ Less Docker overhead

**When to Disable Reuse**:
- 🔄 Testing container initialization logic
- 🗑️ Cleaning up before/after tests
- 🔐 Security-sensitive scenarios

### Shared Network

Dev Services uses Docker's shared network for service-to-service communication:

```properties
quarkus.easynats.devservices.shared=true  # Enable shared network
```

This allows other containers to reference NATS by hostname `nats` instead of IP:

```yaml
# Other containers in Docker Compose can use:
- NATS_URL=nats://nats:4222
```

### Viewing Container Logs

See what's happening in your NATS container:

```bash
# Find the container
docker ps | grep nats

# View logs
docker logs <container-id>

# Follow logs
docker logs -f <container-id>
```

### Manual Container Management

Stop the container manually:

```bash
docker stop quarkus-easynats
```

Remove the container:

```bash
docker rm quarkus-easynats
```

A new one will start automatically on next app run.

## Troubleshooting

### Dev Services Not Starting

**Symptom**: `Unable to start NATS Dev Services container`

**Causes & Solutions**:

1. **Docker not running**
   ```bash
   # Check if Docker is running
   docker ps
   # Start Docker if needed
   ```

2. **Image not found**
   ```bash
   # Pull the image manually
   docker pull nats:2.11
   ```

3. **Port already in use**
   ```properties
   # Use a different port
   quarkus.easynats.devservices.port=4223
   ```

### Explicit Server Configuration Not Recognized

**Symptom**: Dev Services still starts even though you set `quarkus.easynats.servers`

**Solution**: Use environment variables (higher priority):

```bash
# Set via environment variable (takes precedence)
export QUARKUS_EASYNATS_SERVERS=nats://my-server:4222

# Then run your app
./mvnw quarkus:dev
```

### Connection Refused Errors

**Symptom**: `Connection refused: localhost:54321`

**Causes & Solutions**:

1. **Container not started yet**
   - Dev Services takes a few seconds to start
   - Check logs: `docker logs quarkus-easynats`

2. **Port mapping issue**
   - Use fixed port: `quarkus.easynats.devservices.port=4222`
   - Verify with: `docker port quarkus-easynats`

3. **Wrong credentials**
   - Default is `guest/guest`
   - Verify with: `docker exec quarkus-easynats nats-top -s nats://localhost:4222 -u guest -p guest`

### Container Uses Too Much Memory

**Symptom**: Docker container consuming significant memory

**Solution**: Use Alpine image:

```properties
quarkus.easynats.devservices.image-name=nats:2.11-alpine
```

### Tests Interfering with Each Other

**Symptom**: Test data persists between test runs

**Solution**: Disable container reuse for tests:

```properties
# application-test.properties
quarkus.easynats.devservices.shared=false
```

### Port Binding Issue on macOS

**Symptom**: `Address already in use` on localhost

**Solution**: The container might be running on Docker Desktop's VM. Check:

```bash
# View container network
docker inspect quarkus-easynats | grep -A 5 NetworkSettings

# Or use the Docker Desktop GUI to check port mappings
```

## Performance Tips

### 1. Use Fixed Port in Development

```properties
quarkus.easynats.devservices.port=4222
```

Avoids dynamic port discovery overhead.

### 2. Enable Container Reuse

```properties
quarkus.easynats.devservices.shared=true  # Default
```

Saves ~2-3 seconds per app restart.

### 3. Use Alpine Image for Small Footprint

```properties
quarkus.easynats.devservices.image-name=nats:2.11-alpine
```

Reduces memory usage and pull time.

### 4. Increase Docker Resources (if needed)

Docker Desktop settings → Resources → increase memory/CPU

## Integration with CI/CD

### GitHub Actions

```yaml
name: Test
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Run tests
        run: ./mvnw clean test
        env:
          QUARKUS_EASYNATS_DEVSERVICES_ENABLED: 'true'
```

### GitLab CI

```yaml
test:
  image: maven:3.8-openjdk-21
  script:
    - mvn clean test
  variables:
    QUARKUS_EASYNATS_DEVSERVICES_ENABLED: 'true'
  services:
    - docker:dind
```

## Next Steps

- 📖 Read [Configuration Guide](./CONFIGURATION.md) for complete connection setup
- 🚀 Read [Quick Start Guide](./QUICKSTART.md) for messaging patterns
- 🔍 Read [Error Troubleshooting Guide](./ERROR_TROUBLESHOOTING.md) when issues arise

---

**Last Updated**: 2025-11-02
**Feature**: Dev Services for NATS
**Status**: ✅ Complete with shared network and fixed port support
