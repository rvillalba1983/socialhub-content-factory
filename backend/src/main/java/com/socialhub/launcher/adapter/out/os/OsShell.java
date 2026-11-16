package com.socialhub.launcher.adapter.out.os;

import com.socialhub.launcher.domain.HostShell;
import com.socialhub.platform.AppPaths;
import com.socialhub.platform.desktop.Desktop;
import com.socialhub.platform.diagnostics.LogsFolder;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class OsShell implements HostShell {

    private final ConfigurableApplicationContext context;
    private final ObjectProvider<BuildProperties> build;
    private final AppPaths paths;

    OsShell(ConfigurableApplicationContext context, ObjectProvider<BuildProperties> build, AppPaths paths) {
        this.context = context;
        this.build = build;
        this.paths = paths;
    }

    @Override public void openBrowser() { Desktop.openBrowser(); }

    @Override
    public void openLogsFolder() {
        try {
            LogsFolder.open(paths.logs());
        } catch (IOException e) {
            log.warn("No se pudo abrir la carpeta de registros {}: {}", paths.logs(), e.getMessage());
        }
    }

    /** Igual que el botón «Cerrar» de la web: se cierra Spring (motor, PostgreSQL, servidor) y termina el programa. */
    @Override
    public void exit() {
        Thread.ofPlatform().name("cierre").start(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        });
    }

    @Override
    public String version() {
        BuildProperties info = build.getIfAvailable();
        return info == null ? "desconocida" : info.getVersion();
    }
}
