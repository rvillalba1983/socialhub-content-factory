package com.socialhub.operations.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Una llamada a un sistema externo y su resultado. Solo se añaden filas; nunca se modifican. */
public record ExternalOperation(@Nullable Long id, String system, String operation, Outcome outcome, Instant startedAt,
                                long durationMs, @Nullable String traceId, @Nullable String errorType,
                                @Nullable String errorMessage, @Nullable String detail) {

    public enum Outcome { SUCCESS, FAILURE }

    /** Una operación aún sin guardar (sin identificador: lo asigna la base de datos). */
    public static ExternalOperation unsaved(String system, String operation, Outcome outcome, Instant startedAt,
                                            long durationMs, @Nullable String traceId, @Nullable String errorType,
                                            @Nullable String errorMessage, @Nullable String detail) {
        return new ExternalOperation(null, system, operation, outcome, startedAt, durationMs, traceId, errorType,
                errorMessage, detail);
    }
}
