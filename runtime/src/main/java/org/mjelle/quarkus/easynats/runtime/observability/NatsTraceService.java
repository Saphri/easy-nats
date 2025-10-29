package org.mjelle.quarkus.easynats.runtime.observability;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;

/**
 * Service for managing distributed tracing spans across NATS messaging operations.
 * Integrates with OpenTelemetry to create producer and consumer spans and propagate
 * W3C Trace Context headers across NATS message boundaries.
 */
@ApplicationScoped
public class NatsTraceService {

    private static final Logger LOGGER = Logger.getLogger(NatsTraceService.class);

    /**
     * Reusable TextMapGetter for extracting trace context from NATS Headers.
     * This is created once and reused for all trace context extraction operations.
     */
    private static final TextMapGetter<Headers> HEADERS_GETTER = new TextMapGetter<Headers>() {
        @Override
        public Iterable<String> keys(Headers carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Headers carrier, String key) {
            java.util.List<String> values = carrier.get(key);
            return (values != null && !values.isEmpty()) ? values.get(0) : null;
        }
    };

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public NatsTraceService() {
        // No-args constructor for CDI bean instantiation when OpenTelemetry is not explicitly injected
        this(null);
    }

    // Secondary constructor for explicit injection if needed, without @Inject
    // to avoid confusing Arc CDI container
    public NatsTraceService(OpenTelemetry otelInstance) {
        this.openTelemetry = initializeOpenTelemetry(otelInstance);
        this.tracer = this.openTelemetry.getTracer(
            NatsTraceService.class.getCanonicalName(),
            "1.0"
        );
    }

    /**
     * Initializes OpenTelemetry, falling back to GlobalOpenTelemetry.get() or noop if needed.
     */
    private static OpenTelemetry initializeOpenTelemetry(OpenTelemetry otelInstance) {
        if (otelInstance != null) {
            return otelInstance;
        }
        try {
            return GlobalOpenTelemetry.get();
        } catch (Exception e) {
            Logger logger = Logger.getLogger(NatsTraceService.class);
            logger.warn("Failed to get GlobalOpenTelemetry instance, using noop: " + e.getMessage());
            return OpenTelemetry.noop();
        }
    }

    /**
     * Creates a producer span for publishing a message and injects W3C Trace Context
     * headers into the provided headers map.
     *
     * The producer span represents the act of publishing a message to a NATS subject.
     *
     * @param subject the NATS subject being published to
     * @param headers the NATS message headers (will be populated with W3C trace context)
     * @return a Span representing the publishing operation
     */
    public Span createProducerSpan(String subject, Headers headers) {
        Span span = tracer.spanBuilder("NATS publish to " + subject)
            .setSpanKind(SpanKind.PRODUCER)
            .startSpan();

        // Set standard messaging attributes
        span.setAttribute("messaging.system", "nats");
        span.setAttribute("messaging.destination", subject);
        span.setAttribute("messaging.operation", "publish");

        // Inject W3C Trace Context into NATS message headers
        injectTraceContext(headers);

        return span;
    }

    /**
     * Creates a consumer span for receiving a message from a NATS subject.
     * Extracts W3C Trace Context from the message headers to link with the producer span.
     *
     * The consumer span represents the act of receiving and processing a message from a NATS subject.
     *
     * @param subject the NATS subject being consumed from
     * @param message the NATS message (may contain W3C trace context headers)
     * @return a Span representing the consumption and processing operation
     */
    public Span createConsumerSpan(String subject, Message message) {
        // Extract trace context from message headers
        Context extractedContext = extractTraceContext(message);

        Span span = tracer.spanBuilder("NATS receive from " + subject)
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(extractedContext)
            .startSpan();

        // Set standard messaging attributes
        span.setAttribute("messaging.system", "nats");
        span.setAttribute("messaging.destination", subject);
        span.setAttribute("messaging.operation", "receive");

        // Add redelivery flag if this is a redelivery attempt
        if (message != null && message.metaData() != null) {
            try {
                long deliveredCount = message.metaData().deliveredCount();
                if (deliveredCount > 1) {
                    span.setAttribute("messaging.message_redelivered", true);
                    span.setAttribute("messaging.redelivery_count", deliveredCount - 1);
                }
            } catch (Exception e) {
                // Silently ignore if metaData is not available
            }
        }

        return span;
    }

    /**
     * Injects the current W3C Trace Context into NATS message headers.
     * This ensures that distributed traces are properly linked across services.
     *
     * @param headers the NATS message headers to populate with trace context
     */
    private void injectTraceContext(Headers headers) {
        try {
            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            if (propagator != null) {
                Map<String, String> carrier = new HashMap<>();
                propagator.inject(Context.current(), carrier, Map::put);

                // Copy the propagated headers into the NATS headers
                for (Map.Entry<String, String> entry : carrier.entrySet()) {
                    headers.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to inject W3C Trace Context into NATS headers: " + e.getMessage());
        }
    }

    /**
     * Extracts the W3C Trace Context from NATS message headers and returns the
     * corresponding OpenTelemetry Context.
     *
     * If no trace context is found, returns the current context.
     *
     * @param message the NATS message containing potential W3C trace context headers
     * @return the extracted or current Context
     */
    private Context extractTraceContext(Message message) {
        try {
            if (message == null || message.getHeaders() == null) {
                return Context.current();
            }

            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            if (propagator == null) {
                return Context.current();
            }

            // Extract the context from the carrier using the reusable HEADERS_GETTER
            Context extractedContext = propagator.extract(Context.current(), message.getHeaders(), HEADERS_GETTER);
            return extractedContext != null ? extractedContext : Context.current();
        } catch (Exception e) {
            LOGGER.warn("Failed to extract W3C Trace Context from NATS headers: " + e.getMessage());
            return Context.current();
        }
    }

    /**
     * Creates a scope for executing code within a span's context.
     * Used to activate a span so that child spans are automatically linked.
     *
     * @param span the span to activate
     * @return a Scope that must be closed to deactivate the span
     */
    public Scope activateSpan(Span span) {
        return span.makeCurrent();
    }

    /**
     * Marks a span as failed with the given exception.
     * Sets the span status to ERROR and records the exception event.
     *
     * @param span the span to mark as failed
     * @param exception the exception that caused the failure
     */
    public void recordException(Span span, Throwable exception) {
        try {
            span.setStatus(
                StatusCode.ERROR,
                exception != null ? exception.getMessage() : "Unknown error"
            );
            if (exception != null) {
                span.recordException(exception);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to record exception in span: " + e.getMessage());
        }
    }
}
