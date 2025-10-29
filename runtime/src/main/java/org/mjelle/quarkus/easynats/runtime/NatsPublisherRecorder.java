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
        // Always create a fresh NatsTraceService instance which has a no-args constructor
        // This ensures it's always available, using noop if OpenTelemetry is not available
        NatsTraceService traceService = new NatsTraceService();

        if (subject != null) {
            return new NatsPublisher<>(connectionManager, objectMapper, traceService, subject.value());
        }
        return new NatsPublisher<>(connectionManager, objectMapper, traceService);
    }
}
