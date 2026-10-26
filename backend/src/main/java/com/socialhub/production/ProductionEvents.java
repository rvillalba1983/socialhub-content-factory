package com.socialhub.production;

/** Avisos que el módulo de producción publica para otros módulos tras un cambio confirmado. */
public final class ProductionEvents {

    private ProductionEvents() {}

    public sealed interface TaskNotice permits ReviewApproved, SwitchedToManual, TaskCompleted {}

    /** Raúl ha aprobado una revisión: las publicaciones en revisión de esa promoción quedan aprobadas. */
    public record ReviewApproved(String taskId, String promotionId) implements TaskNotice {}

    /** Una tarea fallida pasa a publicación manual con LEEME. */
    public record SwitchedToManual(String taskId, String promotionId) implements TaskNotice {}

    public record TaskCompleted(String taskId) implements TaskNotice {}
}
