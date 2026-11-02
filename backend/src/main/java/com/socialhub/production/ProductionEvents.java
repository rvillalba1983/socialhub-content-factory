package com.socialhub.production;

import org.jmolecules.event.annotation.DomainEvent;
import org.jspecify.annotations.Nullable;

/** Eventos de dominio que producción publica para otros módulos. Se atienden en la misma transacción. */
public final class ProductionEvents {

    private ProductionEvents() {}

    public sealed interface TaskNotice permits ReviewApproved, SwitchedToManual, TaskCompleted {}

    /** Raúl ha aprobado una revisión: las publicaciones en revisión de esa promoción quedan aprobadas. */
    @DomainEvent
    public record ReviewApproved(String taskId, @Nullable String promotionId) implements TaskNotice {}

    /** Una tarea fallida pasa a publicación manual con LEEME. */
    @DomainEvent
    public record SwitchedToManual(String taskId, @Nullable String promotionId) implements TaskNotice {}

    @DomainEvent
    public record TaskCompleted(String taskId) implements TaskNotice {}
}
