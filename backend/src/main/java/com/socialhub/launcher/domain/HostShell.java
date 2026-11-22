package com.socialhub.launcher.domain;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** El sistema operativo y la propia aplicación: navegador, carpeta de registros, versión y cierre. */
@SecondaryPort
public interface HostShell {

    void openBrowser();

    void openLogsFolder();

    /** Cierra la aplicación de forma ordenada, después de volver de la llamada. */
    void exit();

    String version();
}
