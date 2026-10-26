package com.socialhub.production.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Estados de una tarea. El código es el que se guarda en la base de datos y usa la interfaz. */
public enum TaskStatus {
    IDLE("idle", "Inactiva"),
    QUEUED("queued", "En cola"),
    RUNNING("run", "En progreso"),
    WAITING("wait", "En espera"),
    BLOCKED("block", "Bloqueada"),
    ERROR("err", "Error"),
    COMPLETED("ok", "Completada"),
    CANCELLED("cancel", "Cancelada");

    private final String code;
    private final String label;

    TaskStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String code() { return code; }

    public String label() { return label; }

    public static TaskStatus fromCode(String code) {
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de tarea desconocido: " + code));
    }
}
