package com.socialhub.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Carpeta de datos de SocialHub: base de datos, registros y configuración local. */
@Component
public class AppPaths {

    private final Path dataDir;

    public AppPaths(SocialHubProperties properties) {
        String configured = properties.dataDir();
        this.dataDir = (configured == null || configured.isBlank() ? defaultDataDir() : Path.of(configured))
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("No se puede crear la carpeta de datos " + dataDir, e);
        }
    }

    public Path dataDir() { return dataDir; }
    public Path postgresData() { return dataDir.resolve("pgdata"); }
    public Path logs() { return dataDir.resolve("logs"); }
    public Path runtime() { return dataDir.resolve("runtime"); }

    public static Path defaultDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return Path.of(local != null && !local.isBlank() ? local : home + "\\AppData\\Local", "SocialHub");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "SocialHub");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg != null && !xdg.isBlank() ? Path.of(xdg, "socialhub") : Path.of(home, ".local", "share", "socialhub");
    }
}
