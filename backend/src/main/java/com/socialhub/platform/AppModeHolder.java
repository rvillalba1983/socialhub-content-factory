package com.socialhub.platform;

import com.socialhub.platform.live.FactoryChanged;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Modo actual (real o demo). Se recuerda en el archivo {@code mode} de la carpeta de datos, así que al reiniciar
 * SocialHub vuelve al último modo elegido.
 */
@Slf4j
@Component
public class AppModeHolder {

    private final Path file;
    private final boolean demoEnabled;
    private final ApplicationEventPublisher events;
    private volatile AppMode current;

    public AppModeHolder(AppPaths paths, SocialHubProperties properties, ApplicationEventPublisher events) {
        this.file = paths.dataDir().resolve("mode");
        this.demoEnabled = properties.demo().enabled();
        this.events = events;
        this.current = read(properties.defaultMode());
        if (current == AppMode.DEMO && !demoEnabled) current = AppMode.REAL;
        log.info("SocialHub en modo {}", current);
    }

    public AppMode current() { return current; }

    public boolean isDemo() { return current == AppMode.DEMO; }

    public boolean demoEnabled() { return demoEnabled; }

    /** Cambia de modo. Las peticiones siguientes ya usan la base de datos del nuevo modo. */
    public synchronized AppMode switchTo(AppMode mode) {
        if (mode == AppMode.DEMO && !demoEnabled) {
            throw new DomainException("El modo demo está desactivado en esta instalación.");
        }
        if (mode != current) {
            write(mode);
            current = mode;
            log.info("Cambio a modo {}", mode);
            events.publishEvent(new FactoryChanged("mode", mode.name()));
        }
        return current;
    }

    private AppMode read(AppMode fallback) {
        try {
            if (!Files.exists(file)) return fallback;
            return AppMode.valueOf(Files.readString(file).trim());
        } catch (IOException | IllegalArgumentException e) {
            log.warn("No se pudo leer el modo guardado ({}); se usa {}", e.getMessage(), fallback);
            return fallback;
        }
    }

    private void write(AppMode mode) {
        try {
            Path tmp = file.resolveSibling("mode.tmp");
            Files.writeString(tmp, mode.name());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el modo", e);
        }
    }
}
