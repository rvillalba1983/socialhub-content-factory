package com.socialhub.ai.domain;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Un encargo semanal ya creado (o una anotación de la misma tabla, como la activación automática de una promoción).
 * Es único por promoción, tipo y semana.
 */
public record AiAutoOrder(String promotionId, String type, LocalDate weekStart, @Nullable String taskId,
                          Instant createdAt) {

    /** Lo que identifica un encargo semanal. */
    public record Key(String promotionId, String type, LocalDate weekStart) {}

    public Key key() { return new Key(promotionId, type, weekStart); }
}
