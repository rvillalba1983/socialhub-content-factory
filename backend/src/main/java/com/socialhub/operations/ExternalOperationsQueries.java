package com.socialhub.operations;

import com.socialhub.operations.domain.ExternalOperation;
import com.socialhub.operations.domain.OperationPage;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Consulta del registro de operaciones externas. */
@PrimaryPort
public interface ExternalOperationsQueries {

    /** Las operaciones más recientes primero, filtradas por sistema y resultado si se indican. */
    OperationPage list(@Nullable String system, ExternalOperation.@Nullable Outcome outcome, @Nullable Integer offset,
                       @Nullable Integer limit);
}
