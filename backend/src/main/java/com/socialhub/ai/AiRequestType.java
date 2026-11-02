package com.socialhub.ai;

import java.util.Arrays;
import java.util.Optional;

/** Tipos de encargo a Claude, con la estación donde aparecen y su modelo por defecto. */
public enum AiRequestType {
    WEEKLY_SPEC("spec.json de la semana", "gen", "socialhub.weekly-spec"),
    CAPTIONS("Textos y hashtags", "gen", "socialhub.captions"),
    WEEKLY_BALANCE("Balance semanal", "ana", "socialhub.weekly-balance"),
    RESEARCH("Investigación", "inv", "socialhub.research"),
    QUALITY_REVIEW("Revisión de calidad", "rev", "socialhub.quality-review");

    private final String label;
    private final String stationId;
    private final String contractType;

    AiRequestType(String label, String stationId, String contractType) {
        this.label = label;
        this.stationId = stationId;
        this.contractType = contractType;
    }

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
