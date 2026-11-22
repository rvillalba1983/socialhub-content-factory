package com.socialhub.launcher.application;

import com.socialhub.launcher.Launcher;
import com.socialhub.launcher.domain.AutostartEntry;
import com.socialhub.launcher.domain.AutostartPreference;
import com.socialhub.launcher.domain.HostShell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class LauncherService implements Launcher {

    private final AutostartEntry entry;
    private final AutostartPreference preference;
    private final HostShell shell;

    @Override
    public Autostart autostart() {
        boolean available = entry.supported();
        // Se responde con la preferencia (por defecto, activado) y no con el registro de Windows: es una consulta de cada página
        // y consultarlo cuesta lanzar PowerShell. Al arrancar, syncAutostart deja la entrada como dice la preferencia.
        return new Autostart(available, available && preference.get().orElse(true));
    }

    @Override
    public synchronized Autostart setAutostart(boolean enabled) {
        if (!entry.supported()) {
            throw new IllegalArgumentException("Abrir SocialHub al iniciar sesión solo está disponible en Windows, con SocialHub instalado");
        }
        preference.set(enabled);
        if (enabled) entry.register();
        else entry.unregister();
        log.info("Arranque con el sistema: {}", enabled ? "activado" : "desactivado");
        return autostart();
    }

    @Override
    public synchronized void syncAutostart() {
        try {
            if (!entry.supported()) return;
            boolean wanted = preference.get().orElse(true);
            // La primera vez se queda guardado el valor por defecto (activado): así consta que Raúl puede cambiarlo
            if (preference.get().isEmpty()) preference.set(true);
            if (wanted) entry.register();
            else if (entry.exists()) entry.unregister();
        } catch (RuntimeException e) {
            log.warn("No se pudo aplicar el arranque con el sistema: {}", e.getMessage());
        }
    }

    @Override public void openApp() { shell.openBrowser(); }

    @Override public void openLogs() { shell.openLogsFolder(); }

    @Override
    public void quit() {
        log.info("SocialHub se cierra a petición desde la bandeja del sistema");
        shell.exit();
    }

    @Override public String version() { return shell.version(); }
}
