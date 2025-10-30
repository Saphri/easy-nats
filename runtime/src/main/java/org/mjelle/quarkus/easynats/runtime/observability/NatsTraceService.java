package org.mjelle.quarkus.easynats.runtime.observability;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
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
import org.mjelle.quarkus.easynats.runtime.NatsConstants;

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
    private final boolean tracingEnabled;

    /**
     * Creates a new NatsTraceService with CDI-injected OpenTelemetry.
     *
     * OpenTelemetry is provided by Quarkus when the quarkus-opentelemetry extension is present.
     * If OpenTelemetry is not configured or no exporter is available, tracing will be disabled.
     * Constructor injection is automatically detected by Quarkus CDI - no @Inject annotation needed.
     *
     * @param openTelemetry the OpenTelemetry instance provided by Quarkus (may be noop)
     */
    public NatsTraceService(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;

        if (openTelemetry != null && openTelemetry != OpenTelemetry.noop()) {
            this.tracer = openTelemetry.getTracer(
                NatsTraceService.class.getCanonicalName(),
                "1.0"
            );
            this.tracingEnabled = true;
            LOGGER.infof("OpenTelemetry tracing initialized successfully: %s",
                openTelemetry.getClass().getSimpleName());
        } else if (openTelemetry == OpenTelemetry.noop()) {
            this.tracer = null;
            this.tracingEnabled = false;
            LOGGER.warn(
                "OpenTelemetry is configured but using noop implementation (no exporter). " +
                "Distributed tracing will be disabled. " +
                "To enable tracing, add an exporter dependency, e.g., " +
                "'io.quarkus:quarkus-opentelemetry-exporter-jaeger' or " +
                "'io.quarkus:quarkus-opentelemetry-exporter-otlp' and configure it in application.properties"
            );
        } else {
            this.tracer = null;
            this.tracingEnabled = false;
            LOGGER.warn(
                "OpenTelemetry is not available. " +
                "Ensure 'io.quarkus:quarkus-opentelemetry' dependency is present in pom.xml"
            );
        }
    }

    /**
     * Checks if tracing is enabled.
     *
     * @return true if OpenTelemetry is properly configured with an exporter; false otherwise
     */
    public boolean isTracingEnabled() {
        return tracingEnabled;
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
        if (!tracingEnabled || tracer == null) {
            return null;
        }
        Span span = tracer.spanBuilder(NatsConstants.SPAN_NAME_PUBLISH_PREFIX + subject)
            .setSpanKind(SpanKind.PRODUCER)
            .startSpan();

        // Set standard messaging attributes
        span.setAttribute(NatsConstants.MESSAGING_SYSTEM, NatsConstants.MESSAGING_SYSTEM_VALUE);
        span.setAttribute(NatsConstants.MESSAGING_DESTINATION, subject);
        span.setAttribute(NatsConstants.MESSAGING_OPERATION, NatsConstants.OPERATION_PUBLISH);

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
        if (!tracingEnabled || tracer == null) {
            return null;
        }
        // Extract trace context from message headers
        Context extractedContext = extractTraceContext(message);

        Span span = tracer.spanBuilder(NatsConstants.SPAN_NAME_RECEIVE_PREFIX + subject)
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(extractedContext)
            .startSpan();

        // Set standard messaging attributes
        span.setAttribute(NatsConstants.MESSAGING_SYSTEM, NatsConstants.MESSAGING_SYSTEM_VALUE);
        span.setAttribute(NatsConstants.MESSAGING_DESTINATION, subject);
        span.setAttribute(NatsConstants.MESSAGING_OPERATION, NatsConstants.OPERATION_RECEIVE);

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
        if (!tracingEnabled || openTelemetry == null) {
            return;
        }
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
        if (!tracingEnabled || openTelemetry == null) {
            return Context.current();
        }
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
