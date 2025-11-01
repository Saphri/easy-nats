# Configuration Guide

**Feature**: Configuration & Security
**Date**: 2025-10-30

## Overview

This guide covers how to configure the Quarkus EasyNATS extension, including connection settings, authentication, and TLS/SSL security.

---

## Table of Contents

- [Basic Configuration](#basic-configuration)
- [Authentication](#authentication)
- [TLS/SSL Configuration](#tlsssl-configuration)
- [Multiple Servers (Failover)](#multiple-servers-failover)
- [Complete Examples](#complete-examples)
- [Native Image and Reflection](#native-image-and-reflection)
- [Migration from Previous Versions](#migration-from-previous-versions)

---

## Basic Configuration

### Minimal Configuration

The only required configuration is the NATS server URL(s):

```properties
# application.properties
quarkus.easynats.servers=nats://localhost:4222
```

This is sufficient for development and testing with an unauthenticated NATS server.

### Configuration Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkus.easynats.servers` | List<String> | (required) | NATS server URL(s). Multiple servers can be specified for failover. |
| `quarkus.easynats.username` | String | (optional) | Username for NATS authentication |
| `quarkus.easynats.password` | String | (optional) | Password for NATS authentication |
| `quarkus.easynats.tls-configuration-name` | String | (optional) | Name of the TLS configuration from Quarkus TLS registry |
| `quarkus.easynats.log-payloads-on-error` | boolean | `true` | Whether to include message payloads in error logs. Set to `false` in production to prevent sensitive data exposure. |

---

## Authentication

### Username/Password Authentication

For NATS servers requiring authentication, provide both username and password:

```properties
# application.properties
quarkus.easynats.servers=nats://localhost:4222
quarkus.easynats.username=my-nats-user
quarkus.easynats.password=my-secure-password
```

**Important Security Notes:**

- ⚠️ **Never commit credentials to version control**
- ✅ Use environment variables for production credentials
- ✅ Use different credentials per environment (dev, staging, prod)

### Using Environment Variables

```properties
# application.properties
quarkus.easynats.servers=${NATS_SERVERS}
quarkus.easynats.username=${NATS_USERNAME}
quarkus.easynats.password=${NATS_PASSWORD}
```

Then set environment variables:

```bash
export NATS_SERVERS=nats://nats.production.example.com:4222
export NATS_USERNAME=prod-user
export NATS_PASSWORD=prod-secure-password
```

---

## TLS/SSL Configuration

### Overview

EasyNATS integrates with the **Quarkus TLS Registry** for centralized TLS configuration management. This provides:

- ✅ Centralized TLS configuration across all Quarkus extensions
- ✅ Support for custom certificates, keystores, and trust stores
- ✅ Environment-specific TLS settings
- ✅ Automatic certificate reloading

### How TLS Works

The TLS behavior follows this logic:

1. **Server URL determines TLS usage**: Use `tls://` or `wss://` scheme to enable TLS
2. **Quarkus TLS Registry provides certificates**: Configure TLS in Quarkus, reference it in EasyNATS
3. **NATS client handles the connection**: The underlying NATS client uses the provided SSLContext when the URL scheme requires it

**Key Principle**: The extension always provides an SSLContext if TLS is configured in Quarkus, but the NATS client only uses it when connecting to a TLS-enabled server (based on the URL scheme).

### Basic TLS Setup

#### Step 1: Enable TLS in NATS Server URL

Change your server URL to use the `tls://` scheme:

```properties
# application.properties
quarkus.easynats.servers=tls://nats.example.com:4222
```

#### Step 2: Configure Quarkus TLS Registry

**Option A: Use Default TLS Configuration**

If you have a default TLS configuration for your entire Quarkus application:

```properties
# application.properties
quarkus.tls.trust-all=true  # For development only - DO NOT USE IN PRODUCTION

# OR for production with proper certificates:
quarkus.tls.trust-store.pem.certs=certificates/ca.crt
```

EasyNATS will automatically use the default TLS configuration.

**Option B: Use Named TLS Configuration**

For dedicated NATS TLS settings:

```properties
# application.properties

# NATS connection with named TLS config
quarkus.easynats.servers=tls://nats.example.com:4222
quarkus.easynats.tls-configuration-name=nats-tls

# Named TLS configuration for NATS
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/nats-ca.crt
quarkus.tls.nats-tls.key-store.pem.keys=certificates/nats-client-key.pem
quarkus.tls.nats-tls.key-store.pem.certs=certificates/nats-client-cert.pem
```

### TLS Configuration Examples

#### Development: Trust All Certificates (Insecure)

```properties
# ⚠️ DEVELOPMENT ONLY - DO NOT USE IN PRODUCTION
quarkus.easynats.servers=tls://localhost:4222
quarkus.tls.trust-all=true
```

#### Production: Custom CA Certificate

```properties
# Production configuration with proper certificate validation
quarkus.easynats.servers=tls://nats.production.example.com:4222
quarkus.easynats.tls-configuration-name=nats-tls

# Trust specific CA certificate
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/production-ca.crt
```

#### Production: Mutual TLS (mTLS)

For NATS servers requiring client certificates:

```properties
quarkus.easynats.servers=tls://nats.production.example.com:4222
quarkus.easynats.tls-configuration-name=nats-mtls

# Server certificate validation
quarkus.tls.nats-mtls.trust-store.pem.certs=certificates/nats-server-ca.crt

# Client certificate for authentication
quarkus.tls.nats-mtls.key-store.pem.keys=certificates/client-key.pem
quarkus.tls.nats-mtls.key-store.pem.certs=certificates/client-cert.pem
```

#### Using Java KeyStore/TrustStore

```properties
quarkus.easynats.servers=tls://nats.example.com:4222
quarkus.easynats.tls-configuration-name=nats-tls

# Using Java KeyStore format
quarkus.tls.nats-tls.trust-store.jks.path=certificates/truststore.jks
quarkus.tls.nats-tls.trust-store.jks.password=${TRUSTSTORE_PASSWORD}

quarkus.tls.nats-tls.key-store.jks.path=certificates/keystore.jks
quarkus.tls.nats-tls.key-store.jks.password=${KEYSTORE_PASSWORD}
```

### TLS Configuration Reference

For complete TLS configuration options, see the [Quarkus TLS Registry documentation](https://quarkus.io/guides/tls-registry-reference).

Common TLS properties:

| Property | Description | Example |
|----------|-------------|---------|
| `quarkus.tls.trust-all` | Trust all certificates (dev only) | `true` |
| `quarkus.tls.<name>.trust-store.pem.certs` | CA certificate in PEM format | `ca.crt` |
| `quarkus.tls.<name>.key-store.pem.keys` | Client private key in PEM format | `client-key.pem` |
| `quarkus.tls.<name>.key-store.pem.certs` | Client certificate in PEM format | `client-cert.pem` |
| `quarkus.tls.<name>.trust-store.jks.path` | TrustStore in JKS format | `truststore.jks` |
| `quarkus.tls.<name>.key-store.jks.path` | KeyStore in JKS format | `keystore.jks` |

---

## Payload Logging Configuration

### Overview

By default, when deserialization errors occur, EasyNATS includes a truncated preview of the message payload in error logs to aid debugging. In production environments with sensitive data (PII, credentials, etc.), you should disable this feature.

### Disabling Payload Logging (Production)

```properties
# application.properties
quarkus.easynats.log-payloads-on-error=false
```

**With payload logging disabled**, error messages will look like:

```
Message deserialization failed for subject=orders, method=handleOrder, type=OrderData
  Root cause: Cannot deserialize value of type `java.math.BigDecimal` from String "invalid"
  (Payload logging disabled. Set quarkus.easynats.log-payloads-on-error=true to enable)
```

**With payload logging enabled** (default), error messages include the payload:

```
Message deserialization failed for subject=orders, method=handleOrder, type=OrderData
  Root cause: Cannot deserialize value of type `java.math.BigDecimal` from String "invalid"
  Raw payload: {"orderId":"ORD-001","customerId":"CUST-123","amount":"invalid"}
```

### When to Disable

Disable payload logging when:
- ✅ Production environments
- ✅ Messages contain personally identifiable information (PII)
- ✅ Messages contain credentials or API keys
- ✅ Compliance requirements (GDPR, HIPAA, etc.) restrict logging sensitive data
- ✅ Logs are sent to external systems or third-party services

### When to Keep Enabled

Keep payload logging enabled when:
- ✅ Development and testing environments
- ✅ Messages contain non-sensitive data only
- ✅ Debugging deserialization issues
- ✅ You have secure log storage with appropriate access controls

---

## Multiple Servers (Failover)

NATS clients support automatic failover by specifying multiple server URLs. The client will try each server in order until a connection succeeds.

```properties
# application.properties
quarkus.easynats.servers=\
  nats://nats1.example.com:4222,\
  nats://nats2.example.com:4222,\
  nats://nats3.example.com:4222
```

### With TLS

```properties
# All servers should use the same TLS configuration
quarkus.easynats.servers=\
  tls://nats1.example.com:4222,\
  tls://nats2.example.com:4222,\
  tls://nats3.example.com:4222
quarkus.easynats.tls-configuration-name=nats-tls

quarkus.tls.nats-tls.trust-store.pem.certs=certificates/cluster-ca.crt
```

---

## Complete Examples

### Local Development

```properties
# application.properties
quarkus.application.name=my-nats-app
quarkus.easynats.servers=nats://localhost:4222
```

### Development with Authentication

```properties
# application.properties
quarkus.application.name=my-nats-app
quarkus.easynats.servers=nats://localhost:4222
quarkus.easynats.username=dev-user
quarkus.easynats.password=dev-password
```

### Development with TLS

```properties
# application.properties
quarkus.application.name=my-nats-app
quarkus.easynats.servers=tls://localhost:4222
quarkus.tls.trust-all=true  # Development only!
```

### Production with TLS and Authentication

```properties
# application.properties
quarkus.application.name=my-nats-app

# NATS connection
quarkus.easynats.servers=${NATS_SERVERS}
quarkus.easynats.username=${NATS_USERNAME}
quarkus.easynats.password=${NATS_PASSWORD}
quarkus.easynats.tls-configuration-name=nats-tls

# Security: Disable payload logging in production
quarkus.easynats.log-payloads-on-error=false

# TLS configuration
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/production-ca.crt
quarkus.tls.nats-tls.key-store.pem.keys=certificates/client-key.pem
quarkus.tls.nats-tls.key-store.pem.certs=certificates/client-cert.pem
```

Environment variables:

```bash
export NATS_SERVERS=tls://nats1.prod.example.com:4222,tls://nats2.prod.example.com:4222
export NATS_USERNAME=prod-user
export NATS_PASSWORD=prod-secure-password
```

### Production with Kubernetes Secrets

```yaml
# kubernetes-deployment.yaml
apiVersion: v1
kind: Secret
metadata:
  name: nats-credentials
type: Opaque
stringData:
  username: prod-user
  password: prod-secure-password
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-nats-app
spec:
  template:
    spec:
      containers:
      - name: app
        image: my-nats-app:latest
        env:
        - name: NATS_SERVERS
          value: "tls://nats.default.svc.cluster.local:4222"
        - name: NATS_USERNAME
          valueFrom:
            secretKeyRef:
              name: nats-credentials
              key: username
        - name: NATS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nats-credentials
              key: password
        volumeMounts:
        - name: nats-certs
          mountPath: /app/certificates
          readOnly: true
      volumes:
      - name: nats-certs
        secret:
          secretName: nats-tls-certs
```

---

#### New Configuration (v1.0+)

```properties
# ✅ NEW APPROACH
quarkus.easynats.servers=tls://localhost:4222  # Note: tls:// scheme
quarkus.easynats.ssl-enabled=true
quarkus.easynats.tls-configuration-name=nats-tls  # Optional: reference named config

# Configure TLS in Quarkus TLS Registry
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/ca.crt
```

### Why This Change?

1. **Centralized TLS Management**: All Quarkus extensions use the same TLS configuration system
2. **More Flexible**: Support for different certificate formats, keystores, mutual TLS
3. **Security**: Proper certificate validation instead of "trust everything"
4. **Standards**: Follows Quarkus conventions for TLS configuration

### Migration Steps

1. **Change server URL scheme**: `nats://` → `tls://`
2. **Keep `ssl-enabled` property**
3. **Add TLS configuration** using Quarkus TLS Registry
4. **Test connection** with new configuration

---

## Native Image and Reflection

The Quarkus EasyNATS extension automatically detects and registers your `NatsPublisher` and `NatsSubscriber` payload types for reflection, so you do not need to manually configure them with `@RegisterForReflection` in most cases. This includes support for common generic collections like `List<MyType>` and `Map<String, MyType>`.

However, the automatic detection may not cover extremely complex, user-defined generic type hierarchies (e.g., `MyWrapper<T extends SomeClass>`).

If you encounter `ClassNotFoundException` or similar reflection-related errors in your native image, you can fall back to Quarkus's standard reflection registration mechanism. Simply add the `@RegisterForReflection` annotation to the problematic payload class:

```java
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MyComplexGenericPayload {
    // ... fields and methods
}
```

This gives the native image build process the necessary hints to include the class and its members for reflection.

---

## Troubleshooting

### Application fails to start: "At least one NATS server must be configured"

**Cause**: Missing required `quarkus.easynats.servers` property

**Solution**: Add server configuration to `application.properties`:

```properties
quarkus.easynats.servers=nats://localhost:4222
```

### Application fails to start: "Username specified but password is missing"

**Cause**: Only username or only password is configured (both are required)

**Solution**: Provide both credentials:

```properties
quarkus.easynats.username=my-user
quarkus.easynats.password=my-password
```

### TLS connection fails: "PKIX path building failed"

**Cause**: Server certificate is not trusted (self-signed or unknown CA)

**Solutions**:

**Option 1** (Development only): Trust all certificates:
```properties
quarkus.tls.trust-all=true
```

**Option 2** (Production): Add CA certificate to trust store:
```properties
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/ca.crt
```

### Connection fails: "Failed to connect to NATS broker(s) at startup"

**Cause**: NATS server is not running or not accessible

**Solution**:
1. Verify NATS server is running: `nats-server -js`
2. Check server URL is correct
3. Verify network connectivity
4. Check firewall rules

### Credentials appear in logs

**Cause**: URLs with embedded credentials (e.g., `nats://user:pass@host:4222`)

**Solution**: The extension automatically sanitizes URLs in logs, replacing credentials with `***:***`. If you still see credentials:
1. Use separate `username` and `password` properties instead of URL embedding
2. Check your application logs for direct URL logging

---

## Best Practices

### Security

✅ **Do**:
- Use environment variables for credentials in production
- Use Kubernetes Secrets or similar secret management systems
- Enable TLS for production environments
- Use named TLS configurations for clarity
- Validate server certificates in production
- Disable payload logging in production (`log-payloads-on-error=false`)

❌ **Don't**:
- Commit credentials to version control
- Use `trust-all` in production
- Embed credentials in server URLs
- Use self-signed certificates without proper validation
- Leave payload logging enabled when handling sensitive data

### Configuration Organization

```properties
# Group related configuration together
# =================================
# Application
# =================================
quarkus.application.name=my-service

# =================================
# NATS Connection
# =================================
quarkus.easynats.servers=${NATS_SERVERS:nats://localhost:4222}
quarkus.easynats.username=${NATS_USERNAME:}
quarkus.easynats.password=${NATS_PASSWORD:}
quarkus.easynats.tls-configuration-name=nats-tls

# =================================
# NATS TLS
# =================================
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/ca.crt
```

### Environment-Specific Configuration

Use Quarkus profiles for environment-specific settings:

```properties
# application.properties (defaults)
quarkus.easynats.servers=nats://localhost:4222

# application-dev.properties
quarkus.easynats.servers=nats://nats.dev.example.com:4222
quarkus.tls.trust-all=true

# application-prod.properties
quarkus.easynats.servers=${NATS_SERVERS}
quarkus.easynats.tls-configuration-name=nats-tls
quarkus.tls.nats-tls.trust-store.pem.certs=certificates/production-ca.crt
```

Activate profile:
```bash
java -jar app.jar -Dquarkus.profile=prod
```

---

## Further Reading

- [Quarkus Configuration Guide](https://quarkus.io/guides/config)
- [Quarkus TLS Registry Reference](https://quarkus.io/guides/tls-registry-reference)
- [NATS Server Documentation](https://docs.nats.io/)
- [EasyNATS Quick Start](./QUICKSTART.md)

---

**Last Updated**: 2025-10-30
**Configuration Version**: 1.0.0
**Status**: ✅ Complete
