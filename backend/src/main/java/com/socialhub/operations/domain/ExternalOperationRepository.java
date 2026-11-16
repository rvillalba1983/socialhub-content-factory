package com.socialhub.operations.domain;

import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Dónde se guardan las operaciones externas. */
@SecondaryPort
public interface ExternalOperationRepository {

    void saveAll(List<ExternalOperation> operations);

    /** Página de operaciones, las más recientes primero, con los filtros que no sean nulos. */
    OperationPage find(@Nullable String system, ExternalOperation.@Nullable Outcome outcome, int offset, int limit);

    /** Borra las anteriores a ese instante y devuelve cuántas. */
    int deleteOlderThan(Instant limit);
}
