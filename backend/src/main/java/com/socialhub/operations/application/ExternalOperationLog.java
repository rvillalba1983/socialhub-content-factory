package com.socialhub.operations.application;

import com.socialhub.operations.RecordExternalOperations;
import com.socialhub.operations.domain.ExternalOperation;
import com.socialhub.operations.domain.ExternalOperationRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Guarda cada operación externa observada en {@code external_operation}.
 *
 * <p>Las filas se acumulan en memoria y se guardan por lotes cada pocos segundos. Así se registran también
 * las operaciones que ocurren antes de que la base de datos esté lista (el propio arranque de PostgreSQL)
 * y una llamada externa nunca espera a la escritura del registro.
 */
@Slf4j
@Service
class ExternalOperationLog implements RecordExternalOperations {

    static final int MAX_PENDING = 10_000;
    static final Duration RETENTION = Duration.ofDays(30);

    private final ObjectProvider<ExternalOperationRepository> repository;
    private final Clock clock;
    private final Queue<ExternalOperation> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    ExternalOperationLog(ObjectProvider<ExternalOperationRepository> repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void record(ExternalOperation operation) {
        if (size.incrementAndGet() > MAX_PENDING) {
            size.decrementAndGet();
            log.warn("Registro de operaciones externas lleno; se descarta {} {}", operation.system(), operation.operation());
            return;
        }
        pending.add(operation);
    }

    @Override
    public void flush() {
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

    @Override
    public void purge() {
        int deleted = repository.getObject().deleteOlderThan(clock.instant().minus(RETENTION));
        if (deleted > 0) log.info("Borradas {} operaciones externas de hace más de {} días", deleted, RETENTION.toDays());
    }
}
