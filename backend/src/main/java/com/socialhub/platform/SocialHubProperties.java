package com.socialhub.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("socialhub")
public record SocialHubProperties(String dataDir, Postgres postgres, Demo demo) {

    public SocialHubProperties {
        if (postgres == null) postgres = new Postgres(54329, 90, "", "");
        if (demo == null) demo = new Demo(true, true, 1500);
    }

    /**
     * @param port puerto local; 0 elige uno libre
     * @param binariesDir carpeta con bin/pg_ctl (instalador); vacía = binarios incluidos
     * @param runtimeDir dónde se descomprimen los binarios incluidos; vacía = dentro de la carpeta de datos
     */
    public record Postgres(int port, int startTimeoutSeconds, String binariesDir, String runtimeDir) {}

    /**
     * @param enabled carga los datos de ejemplo (marcados como tales) si la base de datos está vacía
     * @param simulate hace avanzar las tareas de ejemplo para ver el anillo en movimiento
     */
    public record Demo(boolean enabled, boolean simulate, long tickMillis) {}
}
