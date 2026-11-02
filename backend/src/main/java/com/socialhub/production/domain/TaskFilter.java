package com.socialhub.production.domain;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Filtros opcionales para listar tareas. */
public record TaskFilter(@Nullable String stationId, @Nullable String promotionId, Set<TaskStatus> statuses,
                         @Nullable Boolean human) {
    public static TaskFilter all() { return new TaskFilter(null, null, Set.of(), null); }
}
