package com.socialhub.launcher.adapter.in.tray;

import com.socialhub.launcher.Launcher;
import com.socialhub.launcher.domain.TrayMenu;

/** Qué hace cada línea del menú de la bandeja. Sin AWT, para poder probarlo. */
final class TrayActions {

    private final Launcher launcher;

    TrayActions(Launcher launcher) { this.launcher = launcher; }

    /** Hace la acción; devuelve el estado del interruptor de arranque si cambió (para repintar el menú), si no, null. */
    Boolean perform(TrayMenu.Action action) {
        switch (action) {
            case OPEN -> launcher.openApp();
            case OPEN_LOGS -> launcher.openLogs();
            case QUIT -> launcher.quit();
            case TOGGLE_AUTOSTART -> {
                return launcher.setAutostart(!launcher.autostart().enabled()).enabled();
            }
        }
        return null;
    }
}
