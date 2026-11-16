package com.socialhub.platform.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Informes de fallo fatal de la JVM ({@code hs_err_pid*.log}). El instalador pide a la JVM que los deje en la carpeta
 * de registros; si no pudo (o se ejecutó de otra forma), la JVM los deja en la carpeta de trabajo o en la temporal.
 * Al arrancar se recogen de ahí (solo los de SocialHub), se avisa en {@code arranque.log} y se borran los de más de
 * 60 días o más allá de los 10 últimos.
 */
public final class JvmCrashReports {

    static final String PREFIX = "hs_err_pid";
    private static final Duration MAX_AGE = Duration.ofDays(60);
    private static final int MAX_KEPT = 10;

    private JvmCrashReports() {}

    /** Recoge los informes sueltos, avisa de los que haya y limpia los viejos. Devuelve los nombres que quedan en {@code logsDir}. */
    public static List<Path> collect(Path logsDir, List<Path> looseFolders) {
        List<Path> kept = new ArrayList<>();
        try {
            Files.createDirectories(logsDir);
            for (Path folder : looseFolders) {
                for (Path report : reports(folder)) {
                    if (belongsToSocialHub(report)) {
                        Path target = logsDir.resolve(report.getFileName());
                        Files.move(report, target, StandardCopyOption.REPLACE_EXISTING);
                        StartupLog.phase("Se ha recogido un informe de fallo fatal de la JVM de una ejecución anterior: " + target);
                    }
                }
            }
            List<Path> all = reports(logsDir);
            all.sort((a, b) -> Long.compare(modified(b), modified(a)));
            Instant limit = Instant.now(Clock.systemUTC()).minus(MAX_AGE);
            for (int i = 0; i < all.size(); i++) {
                if (i >= MAX_KEPT || Instant.ofEpochMilli(modified(all.get(i))).isBefore(limit)) {
                    Files.deleteIfExists(all.get(i));
                } else {
                    kept.add(all.get(i));
                }
            }
            if (!kept.isEmpty()) {
                StartupLog.phase("Hay " + kept.size() + " informe(s) de fallo fatal de la JVM en " + logsDir + " (el más reciente: " + kept.getFirst().getFileName() + ")");
            }
        } catch (IOException | RuntimeException e) {
            StartupLog.error("No se pudieron revisar los informes de fallo de la JVM", e);
        }
        return kept;
    }

    private static List<Path> reports(Path folder) {
        if (!Files.isDirectory(folder)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            return new ArrayList<>(files.filter(p -> p.getFileName().toString().startsWith(PREFIX)
                    && p.getFileName().toString().endsWith(".log")).toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** En la carpeta temporal puede haber informes de otros programas de Java: solo valen los que nombran SocialHub. */
    static boolean belongsToSocialHub(Path report) {
        try (Stream<String> lines = Files.lines(report, StandardCharsets.ISO_8859_1)) {
            return lines.limit(400).anyMatch(l -> l.toLowerCase(Locale.ROOT).contains("socialhub"));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
