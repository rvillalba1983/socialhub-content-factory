package com.socialhub.platform.web;

import com.socialhub.api.AppApi;
import com.socialhub.api.model.AppVersionDto;
import com.socialhub.api.model.LogsFolderDto;
import com.socialhub.platform.AppPaths;
import com.socialhub.platform.diagnostics.LogsFolder;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Cerrar SocialHub desde la web (no tiene ventana propia) y decir qué versión es. */
@Slf4j
@RestController
@RequiredArgsConstructor
class AppController implements AppApi {

    private final ConfigurableApplicationContext context;
    /** Vacío si se ejecuta sin compilar con Maven (por ejemplo, desde el IDE): no existe build-info.properties. */
    private final ObjectProvider<BuildProperties> build;
    private final AppPaths paths;

    @Override
    public ResponseEntity<AppVersionDto> getAppVersion() {
        BuildProperties info = build.getIfAvailable();
        if (info == null) {
            return ResponseEntity.ok(new AppVersionDto("desconocida", "local", OffsetDateTime.now(ZoneOffset.UTC)));
        }
        String commit = info.get("commit");
        return ResponseEntity.ok(new AppVersionDto(
                info.getVersion(),
                commit == null || commit.isBlank() ? "local" : commit,
                info.getTime().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<LogsFolderDto> getLogsFolder() {
        return ResponseEntity.ok(new LogsFolderDto(paths.logs().toString()));
    }

    @Override
    public ResponseEntity<Void> openLogsFolder(String requestGuard) {
        try {
            LogsFolder.open(paths.logs());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo abrir la carpeta de registros " + paths.logs() + ": " + e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> shutdownApp(String requestGuard) {
        log.info("SocialHub se cierra a petición desde la web");
        // Después de responder: se cierra Spring (motor, PostgreSQL, servidor) y termina el programa
        Thread.ofPlatform().name("cierre").start(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        });
        return ResponseEntity.accepted().build();
    }
}
