package org.mjelle.quarkus.easynats.runtime;

import io.quarkus.runtime.annotations.Recorder;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.mjelle.quarkus.easynats.NatsConnectionManager;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.runtime.observability.NatsTraceService;

@Recorder
public class NatsPublisherRecorder {

    /**
     * CDI producer for {@link NatsPublisher} instances.
     * <p>
     * This method is called by CDI to produce instances of {@link NatsPublisher}.
     * It uses the {@link NatsSubject} annotation to configure the default subject for the publisher.
     * <p>
     * NatsTraceService is injected by CDI at runtime. OpenTelemetry will be injected
     * automatically by Quarkus when the quarkus-opentelemetry extension is present.
     * <p>
     * The global {@link Codec} bean is also injected and used for serialization of message payloads.
     *
     * @param injectionPoint      the injection point
     * @param connectionManager   the NATS connection manager
     * @param codec               the global payload codec (injected by CDI)
     * @param traceService        the NATS tracing service (may be a no-op implementation if tracing is disabled)
     * @param <T>                 the type of the publisher
     * @return a configured {@link NatsPublisher} instance
     */
    @Produces
    @Dependent
    public <T> NatsPublisher<T> publisher(
            InjectionPoint injectionPoint,
            NatsConnectionManager connectionManager,
            Codec codec,
            NatsTraceService traceService) {
        NatsSubject subject = injectionPoint.getAnnotated().getAnnotation(NatsSubject.class);

        if (subject != null) {
            return new NatsPublisher<>(connectionManager, codec, traceService, subject.value());
        }
        return new NatsPublisher<>(connectionManager, codec, traceService);
    }
}
