package com.socialhub.operations;

import com.socialhub.platform.observability.ExternalCallContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recoge cada operación externa observada y la guarda en {@code external_operation}.
 *
 * <p>Las filas se acumulan en memoria y se guardan por lotes cada pocos segundos. Así se registran también
 * las operaciones que ocurren antes de que la base de datos esté lista (el propio arranque de PostgreSQL)
 * y una llamada externa nunca espera a la escritura del registro.
 */
@Slf4j
@Component
class ExternalOperationRecorder implements ObservationHandler<ExternalCallContext> {

    static final int MAX_PENDING = 10_000;
    static final Duration RETENTION = Duration.ofDays(30);

    private final ObjectProvider<ExternalOperationRepository> repository;
    private final Clock clock;
    private final Queue<ExternalOperation> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    ExternalOperationRecorder(ObjectProvider<ExternalOperationRepository> repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ExternalCallContext;
    }

    @Override
    public void onStop(ExternalCallContext context) {
        Throwable error = context.getError();
        long durationMs = Duration.between(context.getStartedAt(), clock.instant()).toMillis();
        var operation = new ExternalOperation(null, context.getSystem(), context.getOperation(),
                error == null ? ExternalOperation.Outcome.SUCCESS : ExternalOperation.Outcome.FAILURE,
                context.getStartedAt(), durationMs, context.getTraceId(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : truncate(String.valueOf(error.getMessage()), 2000),
                context.getDetail());
        if (error == null) {
            log.debug("Operación externa {} {} correcta en {} ms", context.getSystem(), context.getOperation(), durationMs);
        } else {
            log.warn("Operación externa {} {} FALLIDA en {} ms: {}", context.getSystem(), context.getOperation(),
                    durationMs, error.toString());
        }
        if (size.incrementAndGet() > MAX_PENDING) {
            size.decrementAndGet();
            log.warn("Registro de operaciones externas lleno; se descarta {} {}", context.getSystem(), context.getOperation());
            return;
        }
        pending.add(operation);
    }

    @Scheduled(fixedDelayString = "${socialhub.operations.flush-millis:2000}")
    void flush() {
        List<ExternalOperation> batch = new ArrayList<>();
        ExternalOperation next;
        while ((next = pending.poll()) != null) {
            size.decrementAndGet();
            batch.add(next);
        }
        if (batch.isEmpty()) return;
        try {
            repository.getObject().saveAll(batch);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar el registro de {} operaciones externas: {}", batch.size(), e.getMessage());
        }
    }

    @Scheduled(cron = "${socialhub.operations.purge-cron:0 30 3 * * *}")
    void purge() {
        int deleted = repository.getObject().deleteOlderThan(clock.instant().minus(RETENTION));
        if (deleted > 0) log.info("Borradas {} operaciones externas de hace más de {} días", deleted, RETENTION.toDays());
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
