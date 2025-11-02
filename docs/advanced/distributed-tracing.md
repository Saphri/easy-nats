# Distributed Tracing Guide

This guide explains the distributed tracing capabilities in Quarkus EasyNATS, which provide end-to-end observability across your messaging pipeline.

## Overview

Quarkus EasyNATS integrates with [OpenTelemetry](https://opentelemetry.io/) to automatically create and propagate distributed traces across NATS messaging operations. This enables you to:

- **Track message flow** across producers and consumers
- **Correlate messages** with their original producer via trace context
- **Detect redeliveries** and failures
- **Monitor latency** and error rates
- **Integrate with APM solutions** (Jaeger, Datadog, New Relic, etc.)

## How It Works

### W3C Trace Context Propagation

Quarkus EasyNATS uses the **W3C Trace Context standard** to propagate trace information through message headers:

1. When you publish a message, a **producer span** is created
2. The current trace context is injected into the message headers as `traceparent` and `tracestate`
3. When a subscriber receives the message, a **consumer span** is created
4. The trace context is extracted from the message headers and linked to the producer span

This creates a complete distributed trace showing the message's journey from producer to consumer.

### Span Attributes

All NATS messaging spans include these standard attributes:

| Attribute | Type | Example |
|-----------|------|---------|
| `messaging.system` | String | `"nats"` |
| `messaging.destination` | String | `"orders.created"` |
| `messaging.operation` | String | `"publish"` or `"receive"` |
| `messaging.message_redelivered` | Boolean | `true` or `false` |
| `messaging.redelivery_count` | Integer | `2` |

## Getting Started

### 1. Enable OpenTelemetry

Add the Quarkus OpenTelemetry extension to your project:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

### 2. Configure an Exporter

Configure OpenTelemetry to export traces to your APM backend. For example, to export to Jaeger:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry-exporter-jaeger</artifactId>
</dependency>
```

Then add to `application.properties`:

```properties
# Jaeger Exporter Configuration
otel.exporter.jaeger.endpoint=http://localhost:14268/api/traces
otel.service.name=my-nats-service
```

### 3. No Additional Code Required

Tracing is **automatic** and **transparent**. No additional code is needed—simply use `NatsPublisher` and `@NatsSubscriber` as normal, and traces will be automatically created and exported.

## Example: Traced Message Flow

### Producer Code

```java
@ApplicationScoped
public class OrderService {

    @Inject
    @NatsSubject("orders.created")
    NatsPublisher<Order> publisher;

    public void createOrder(Order order) {
        // This automatically creates a producer span
        // and injects W3C Trace Context headers
        publisher.publish(order);
    }
}
```

### Consumer Code

```java
@ApplicationScoped
public class OrderProcessor {

    @NatsSubscriber(subject = "orders.created")
    public void onOrderCreated(Order order) {
        // This automatically creates a consumer span
        // linked to the producer span via trace context
        processOrder(order);
    }

    private void processOrder(Order order) {
        // Your business logic here
    }
}
```

### Resulting Trace

The trace would show:

```
Trace: abc123def456
├── Producer Span: "NATS publish to orders.created" (duration: 5ms)
│   └── Headers: traceparent=00-abc123def456-span001-01
│   └── Headers: tracestate=vendor-specific-state
└── Consumer Span: "NATS receive from orders.created" (duration: 150ms)
    └── Attributes: messaging.message_redelivered=false
    └── Child spans: (any child spans created during processing)
```

## Advanced Configuration

### Custom Span Attributes

While basic attributes are automatic, you can add custom attributes by accessing the current span via OpenTelemetry:

```java
import io.opentelemetry.api.trace.Span;

@ApplicationScoped
public class OrderProcessor {

    @NatsSubscriber(subject = "orders.created")
    public void onOrderCreated(Order order) {
        // Get the current span (automatically created by EasyNATS)
        Span span = Span.current();

        // Add custom attributes
        span.setAttribute("order.id", order.getId());
        span.setAttribute("order.amount", order.getAmount());
        span.setAttribute("order.customer_id", order.getCustomerId());

        processOrder(order);
    }
}
```

### Disabling Tracing

To disable automatic tracing (if needed), you can use OpenTelemetry's configuration:

```properties
otel.sdk.disabled=true
```

However, this disables all tracing in the application, not just NATS. For more granular control, consider using sampling configurations.

## Redelivery Detection

When a message is redelivered (due to failure or timeout), the consumer span automatically includes:

- `messaging.message_redelivered=true`
- `messaging.redelivery_count=N` (number of redelivery attempts)

This allows you to identify and analyze redelivered messages in your APM system.

## Error Handling

Errors during message processing are automatically recorded in the consumer span:

- **Span Status:** Set to `ERROR`
- **Exception Details:** The exception message and stack trace are recorded
- **Attributes:** Error-related attributes may be added

This allows you to quickly identify failures and debug production issues.

## Compatibility

Distributed tracing in Quarkus EasyNATS is:

- ✅ **Compatible with all NATS messaging patterns** (publish/subscribe, JetStream, durable consumers)
- ✅ **Requires OpenTelemetry** (must be configured; fails fast with clear error messages if not available)
- ✅ **Standards-compliant** (uses W3C Trace Context and OpenTelemetry standards)
- ✅ **Native-image friendly** (works in both JVM and GraalVM native image)

## Troubleshooting

### Traces Not Appearing

1. Verify OpenTelemetry extension is added to `pom.xml`
2. Check that an exporter is configured (Jaeger, Otlp, etc.)
3. Verify the exporter endpoint is reachable
4. Check application logs for OpenTelemetry warnings

### Performance Impact

Tracing has minimal performance impact:

- Trace context injection/extraction: < 1ms per message
- Span creation: < 0.5ms per message
- No additional database queries or external calls

If performance is critical, consider using sampling:

```properties
otel.traces.sampler=parentbased_traceidratio
otel.traces.sampler.arg=0.1  # Sample 10% of traces
```

## References

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [W3C Trace Context Specification](https://w3c.github.io/trace-context/)
- [Quarkus OpenTelemetry Extension](https://quarkus.io/guides/opentelemetry)
- [Distributed Tracing Feature Specification](../specs/012-distributed-tracing-spans/spec.md)
