package org.mjelle.quarkus.easynats.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.Recorder;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;

@Recorder
public class NatsPublisherRecorder {

    /**
     * CDI producer for {@link NatsPublisher} instances.
     * <p>
     * This method is called by CDI to produce instances of {@link NatsPublisher}.
     * It uses the {@link NatsSubject} annotation to configure the default subject for the publisher.
     * <p>
     * NatsTraceService is created directly to work around build-time CDI constraints, but OpenTelemetry
     * is now required and its absence will cause NatsTraceService to fail with a clear error message.
     *
     * @param injectionPoint      the injection point
     * @param connectionManager   the NATS connection manager
     * @param objectMapper        the Jackson object mapper
     * @param <T>                 the type of the publisher
     * @return a configured {@link NatsPublisher} instance
     */
    @Produces
    @Dependent
    public <T> NatsPublisher<T> publisher(
            InjectionPoint injectionPoint,
            NatsConnectionManager connectionManager,
            ObjectMapper objectMapper) {
        NatsSubject subject = injectionPoint.getAnnotated().getAnnotation(NatsSubject.class);
        // Create NatsTraceService - will fail fast with clear error if OpenTelemetry is not available
        NatsTraceService traceService = new NatsTraceService();

        if (subject != null) {
            return new NatsPublisher<>(connectionManager, objectMapper, traceService, subject.value());
        }
        return new NatsPublisher<>(connectionManager, objectMapper, traceService);
    }
}
