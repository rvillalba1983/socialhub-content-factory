package com.socialhub.production.domain;

/** Orden que la interfaz puede ofrecer ahora mismo para una tarea. */
public record AvailableCommand(TaskCommandType type, String label, boolean needsConfirmation) {
    static AvailableCommand of(TaskCommandType type) {
        return new AvailableCommand(type, type.label(), type.needsConfirmation());
    }
}
