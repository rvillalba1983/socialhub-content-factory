package com.socialhub.platform;

/**
 * Modo de trabajo. Cada modo tiene su propia base de datos y nunca se mezclan:
 * <ul>
 *   <li>{@link #REAL}: los datos de Raúl. El motor ejecuta las tareas de verdad.</li>
 *   <li>{@link #DEMO}: datos de ejemplo marcados como tales. El simulador los mueve; nada actúa sobre servicios reales.</li>
 * </ul>
 */
public enum AppMode {
    REAL("socialhub"),
    DEMO("socialhub_demo");

    private final String database;

    AppMode(String database) { this.database = database; }

    /** Nombre de la base de datos de PostgreSQL de este modo. */
    public String database() { return database; }
}
