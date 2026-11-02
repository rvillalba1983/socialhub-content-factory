package com.socialhub.production.domain;

/** Órdenes que se pueden dar a una tarea. Cuáles están permitidas depende de su estado. */
public enum TaskCommandType {
    PAUSE("Pausar"),
    RESUME("Reanudar"),
    CANCEL("Cancelar"),
    DISCARD("Descartar"),
    RETRY("Reintentar"),
    SWITCH_TO_MANUAL("Pasar a manual con LEEME"),
    RUN_NOW("Ejecutar ahora"),
    RESCHEDULE("Reprogramar"),
    RECHECK("Comprobar de nuevo"),
    START("Aprobar e iniciar"),
    RERUN("Volver a ejecutar"),
    REOPEN("Reactivar"),
    APPROVE("Aprobar como Raúl"),
    MARK_DONE("Marcar como hecho"),
    REMIND("Avisar a Raúl"),
    CHANGE_AGENT("Cambiar agente");

    private final String label;

    TaskCommandType(String label) { this.label = label; }

    public String label() { return label; }

    /** Las que no tienen vuelta atrás fácil piden confirmación en la interfaz. */
    public boolean needsConfirmation() { return this == CANCEL || this == DISCARD; }
}
