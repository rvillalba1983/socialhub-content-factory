package com.socialhub.ai;

import java.util.Arrays;
import java.util.Optional;

/** Tipos de encargo a Claude, con la estación donde aparecen y su modelo por defecto. */
public enum AiRequestType {
    WEEKLY_SPEC("spec.json de la semana", "gen", "socialhub.weekly-spec"),
    CAPTIONS("Textos y hashtags", "gen", "socialhub.captions"),
    WEEKLY_BALANCE("Balance semanal", "ana", "socialhub.weekly-balance"),
    RESEARCH("Investigación", "inv", "socialhub.research"),
    QUALITY_REVIEW("Revisión de calidad", "rev", "socialhub.quality-review"),
    WEEKLY_PROPOSALS("Propuestas de la semana", "est", "socialhub.weekly-proposals"),
    PUBLER_SCHEDULE("Programar en Publer", "prog", "socialhub.publer-schedule", true),
    PUBLISH_CHECK("Comprobar publicación", "pub", "socialhub.publish-check", true),
    CALENDAR_READ("Leer calendario de Publer y la red", "prog", "socialhub.calendar-read", true),
    METRICS_READ("Leer métricas", "ana", "socialhub.metrics-read", true);

    private final String label;
    private final String stationId;
    private final String contractType;
    private final boolean onPc;

    AiRequestType(String label, String stationId, String contractType) {
        this(label, stationId, contractType, false);
    }

    AiRequestType(String label, String stationId, String contractType, boolean onPc) {
        this.label = label;
        this.stationId = stationId;
        this.contractType = contractType;
        this.onPc = onPc;
    }

    /**
     * Lo hace Claude con Chrome en el PC de Raúl (va a «para-claude-pc»). Lo crea SocialHub al publicar, no Raúl, y no
     * cuenta en el límite diario: tiene hora fija y no puede esperar.
     */
    public boolean onPc() { return onPc; }

    public String label() { return label; }

    public String stationId() { return stationId; }

    /** Tipo en el encargo que lee Claude («type» del contrato). */
    public String contractType() { return contractType; }

    /** Tipo de la tarea en SocialHub. */
    public String taskType() { return "AI_" + name(); }

    public static Optional<AiRequestType> ofTaskType(String taskType) {
        return Arrays.stream(values()).filter(t -> t.taskType().equals(taskType)).findFirst();
    }
}
