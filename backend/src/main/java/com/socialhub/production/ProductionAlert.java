package com.socialhub.production;

/**
 * Algo de producción que necesita atención de Raúl: una tarea con error o bloqueada, o una estación bloqueada o caída.
 *
 * @param stationId estación a la que pertenece (la propia estación, si el aviso es de una estación)
 */
public record ProductionAlert(Kind kind, Target target, String targetId, String stationId, String title, String detail) {

    public enum Kind { FAILED, BLOCKED }

    public enum Target { TASK, STATION }
}
