# TLS/SSL Configuration

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


```