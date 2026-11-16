package com.socialhub.launcher;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Lo que se puede hacer con SocialHub como programa de escritorio (desde la bandeja, el arranque o la web). */
@PrimaryPort
public interface Launcher {

    /** {@code available}: este sistema permite abrirlo al iniciar sesión (Windows con el instalador). */
    record Autostart(boolean available, boolean enabled) {}

    Autostart autostart();

    /** Activa o desactiva el arranque con el sistema. @throws IllegalArgumentException si este sistema no lo permite */
    Autostart setAutostart(boolean enabled);

    /**
     * Al arrancar: aplica la preferencia (por defecto, activado) y deja la entrada de arranque apuntando a este programa.
     * Nunca lanza: si no se puede, queda anotado y SocialHub sigue.
     */
    void syncAutostart();

    /** Abre SocialHub en el navegador. */
    void openApp();

    /** Abre la carpeta de registros. */
    void openLogs();

    /** Cierra SocialHub de forma ordenada (servidor y PostgreSQL), como el botón «Cerrar» de la web. */
    void quit();

    /** Versión instalada, para enseñarla en el menú. */
    String version();
}
