package com.socialhub.platform.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Punto único por el que pasa toda operación externa a SocialHub (PostgreSQL, Drive, Publer, APIs…).
 *
 * <p>Cada llamada se observa con Micrometer: deja una métrica ({@value #NAME}, con sistema, operación y error),
 * un identificador de traza en los registros y, a través del módulo {@code operations}, una fila en la tabla
 * {@code external_operation} que se ve en la pantalla «Operaciones externas».
 */
@Component
public class ExternalCalls {

    public static final String NAME = "socialhub.external.call";

    private final ObservationRegistry registry;
    private final @Nullable Tracer tracer;
    private final Clock clock;

    @Autowired
    public ExternalCalls(ObservationRegistry registry, ObjectProvider<Tracer> tracer, Clock clock) {
        this(registry, tracer.getIfAvailable(), clock);
    }

    ExternalCalls(ObservationRegistry registry, @Nullable Tracer tracer, Clock clock) {
        this.registry = registry;
        this.tracer = tracer;
        this.clock = clock;
    }

    /** Sin observación: para pruebas que crean piezas a mano. */
    public static ExternalCalls unobserved() {
        return new ExternalCalls(ObservationRegistry.NOOP, (Tracer) null, Clock.systemUTC());
    }

    public <T> T call(String system, String operation, @Nullable String detail, Supplier<T> action) {
        ExternalCallContext context = new ExternalCallContext(system, operation, detail, clock.instant());
        Observation observation = Observation.createNotStarted(NAME, () -> context, registry)
                .contextualName(system + " " + operation)
                .lowCardinalityKeyValue("system", system)
                .lowCardinalityKeyValue("operation", operation);
        return observation.observe(() -> {
            context.setTraceId(currentTraceId());
            return action.get();
        });
    }

    public void run(String system, String operation, @Nullable String detail, Runnable action) {
        call(system, operation, detail, () -> {
            action.run();
            return null;
        });
    }

    private @Nullable String currentTraceId() {
        if (tracer == null) return null;
        Span span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
