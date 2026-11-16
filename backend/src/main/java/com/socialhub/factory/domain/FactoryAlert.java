package com.socialhub.factory.domain;

import org.jspecify.annotations.Nullable;

/**
 * Algo que necesita la atención de Raúl, de cualquier parte de la fábrica. {@code targetId} es la tarea, la estación,
 * el elemento de la armería o la publicación, según {@code targetType}.
 *
 * @param action botón del aviso, si lo tiene
 */
public record FactoryAlert(Kind kind, TargetType targetType, String targetId, String stationId, String title, String detail,
                           @Nullable Action action) {

    public enum Kind { FAILED, BLOCKED }

    public enum TargetType { TASK, STATION, ARMORY, PUBLICATION }

    /** Pasa ya a Publora las publicaciones retenidas por el límite de esa cuenta de Publer. */
    public record Action(String label, String accountName, String platform) {}

    public FactoryAlert(Kind kind, TargetType targetType, String targetId, String stationId, String title, String detail) {
        this(kind, targetType, targetId, stationId, title, detail, null);
    }
}
