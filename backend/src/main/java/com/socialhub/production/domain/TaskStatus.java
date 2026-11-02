package com.socialhub.production.domain;

/** Estados de una tarea. Los nombres coinciden con los de la API y la base de datos. */
public enum TaskStatus {
    IDLE("Inactiva"),
    QUEUED("En cola"),
    RUNNING("En progreso"),
    WAITING("En espera"),
    BLOCKED("Bloqueada"),
    FAILED("Error"),
    COMPLETED("Completada"),
    CANCELLED("Cancelada");

    private final String label;

    TaskStatus(String label) { this.label = label; }

    public String label() { return label; }
}
