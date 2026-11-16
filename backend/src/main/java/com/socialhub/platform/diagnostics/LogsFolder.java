package com.socialhub.platform.diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Abre la carpeta de registros en el explorador del sistema. La ruta es siempre la de SocialHub, nunca una que llegue de fuera. */
public final class LogsFolder {

    private LogsFolder() {}

    public static void open(Path folder) throws IOException {
        Files.createDirectories(folder);
        new ProcessBuilder(command(System.getProperty("os.name", ""), folder))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    /** Sin intérprete de órdenes: el programa y la ruta van como argumentos separados. */
    static List<String> command(String osName, Path folder) {
        String os = osName.toLowerCase(Locale.ROOT);
        String path = folder.toAbsolutePath().toString();
        return os.contains("win") ? List.of("explorer.exe", path)
                : os.contains("mac") ? List.of("open", path)
                : List.of("xdg-open", path);
    }
}
