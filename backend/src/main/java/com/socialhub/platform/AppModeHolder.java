package com.socialhub.platform;

import com.socialhub.platform.live.FactoryChanged;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Modo actual (real o demo). Se recuerda en el archivo {@code mode} de la carpeta de datos, así que al reiniciar
 * SocialHub vuelve al último modo elegido.
 *
 * <p>El modo elegido en la interfaz decide qué base de datos ven las peticiones. Los procesos en segundo plano
 * (el motor de tareas en modo real, el simulador en modo demo) fijan su modo con {@link #callIn}, así que siguen
 * trabajando sobre su base de datos aunque Raúl cambie de modo en la interfaz.
 */
@Slf4j
@Component
public class AppModeHolder {

    private final Path file;
    private final boolean demoEnabled;
    private final ApplicationEventPublisher events;
    private static final ThreadLocal<AppMode> PINNED = new ThreadLocal<>();
    /** Motivo por el que SocialHub está parado (por ejemplo, tras restaurar una copia), o null. */
    private static volatile @org.jspecify.annotations.Nullable String halted;

    private volatile AppMode current;

    public AppModeHolder(AppPaths paths, SocialHubProperties properties, ApplicationEventPublisher events) {
        this.file = paths.dataDir().resolve("mode");
        this.demoEnabled = properties.demo().enabled();
        this.events = events;
        this.current = read(properties.defaultMode());
        if (current == AppMode.DEMO && !demoEnabled) current = AppMode.REAL;
        log.info("SocialHub en modo {}", current);
    }

    /** Modo que se aplica en este hilo: el fijado con {@link #callIn} o, si no hay, el elegido en la interfaz. */
    public AppMode current() {
        AppMode pinned = PINNED.get();
        return pinned != null ? pinned : current;
    }

    /** Modo elegido en la interfaz. */
    public AppMode selected() { return current; }

    public boolean isDemo() { return current() == AppMode.DEMO; }

    /** Ejecuta {@code action} con el modo fijado (y por tanto su base de datos), sea cual sea el de la interfaz. */
    public static <T> T callIn(AppMode mode, Supplier<T> action) {
        AppMode previous = PINNED.get();
        PINNED.set(mode);
        try {
            return action.get();
        } finally {
            if (previous == null) PINNED.remove(); else PINNED.set(previous);
        }
    }

    /** Trabajo en segundo plano con ese modo. No hace nada si SocialHub está parado (ver {@link #halt}). */
    public static void runIn(AppMode mode, Runnable action) {
        if (halted != null) return;
        callIn(mode, () -> { action.run(); return null; });
    }

    /**
     * Para el trabajo en segundo plano y los cambios desde la interfaz hasta reabrir SocialHub (tras restaurar una
     * copia, para que nada escriba sobre los datos recién cargados).
     */
    public static void halt(String reason) { halted = reason; }

    public static @org.jspecify.annotations.Nullable String haltedReason() { return halted; }

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
