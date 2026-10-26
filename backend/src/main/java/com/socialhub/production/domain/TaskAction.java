package com.socialhub.production.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Acciones que se pueden pedir sobre una tarea. Cuáles están permitidas depende de su estado. */
public enum TaskAction {
    PAUSE("pause", "Pausar"),
    RESUME("resume", "Reanudar"),
    CANCEL("cancel", "Cancelar"),
    DISCARD("discard", "Descartar"),
    RETRY("retry", "Reintentar"),
    SWITCH_TO_MANUAL("manual", "Pasar a manual con LEEME"),
    RUN_NOW("runnow", "Ejecutar ahora"),
    RESCHEDULE("resched", "Reprogramar"),
    RECHECK("recheck", "Comprobar de nuevo"),
    START("start", "Aprobar e iniciar"),
    RERUN("rerun", "Volver a ejecutar"),
    REOPEN("reopen", "Reactivar"),
    APPROVE("approve", "Aprobar como Raúl"),
    MARK_DONE("done", "Marcar como hecho"),
    REMIND("remind", "Avisar a Raúl"),
    CHANGE_AGENT("agent", "Cambiar agente");

    private final String code;
    private final String label;

    TaskAction(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String code() { return code; }

    public String label() { return label; }

    /** Acciones que cambian la tarea de forma irreversible o con efecto externo y piden confirmación. */
    public boolean needsConfirmation() { return this == CANCEL || this == DISCARD; }

    public static TaskAction fromCode(String code) {
        return Arrays.stream(values()).filter(a -> a.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Acción desconocida: " + code));
    }
}
