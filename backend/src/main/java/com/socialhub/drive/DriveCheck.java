package com.socialhub.drive;

import com.socialhub.armory.ArmoryService;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.promotion.PromotionQueries;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada 30 s, en modo real: ¿están accesibles todas las carpetas con papel y las de «Recursos»? El resultado se
 * guarda en la armería (y sale en los avisos si falta alguna). Solo se guarda cuando cambia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DriveCheck {

    static final String ID = "google-drive";
    static final String NAME = "Google Drive (carpetas)";
    static final String DESCRIPTION = "Carpetas de Drive sincronizadas en este ordenador (G:). SocialHub lee y escribe "
            + "en ellas; no crea ni mueve nada fuera de lo que le toca.";

    private final PromotionQueries promotions;
    private final ArmoryService armory;
    private volatile @Nullable String last;

    record Result(String status, String note) {}

    @Scheduled(initialDelayString = "${socialhub.drive.check-initial-delay-millis:5000}",
               fixedDelayString = "${socialhub.drive.check-millis:30000}")
    void check() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                Result result = evaluate(new LinkedHashSet<>(promotions.allFolders()));
                String key = result.status() + "|" + result.note();
                if (!Objects.equals(key, last)) {
                    armory.registerService(ID, NAME, DESCRIPTION, result.status(), result.note(),
                            List.of("Leer material", "Guardar piezas", "Encargos"));
                    if (last != null || !"OK".equals(result.status())) log.info("Drive: {} · {}", result.status(), result.note());
                    last = key;
                }
            } catch (RuntimeException e) {
                log.warn("Comprobación de Drive omitida: {}", e.toString());
            }
        });
    }

    static Result evaluate(Set<String> folders) {
        if (folders.isEmpty()) return new Result("IDLE", "Sin carpetas configuradas");
        List<String> missing = new ArrayList<>();
        for (String folder : folders) {
            if (!exists(folder)) missing.add(folder);
        }
        if (missing.isEmpty()) {
            return new Result("OK", folders.size() == 1 ? "1 carpeta accesible" : folders.size() + " carpetas accesibles");
        }
        return new Result("BLOCKED", "No disponible" + (missing.size() == 1 ? ": " : "s (" + missing.size() + "): ")
                + String.join(" · ", missing.subList(0, Math.min(3, missing.size())))
                + (missing.size() > 3 ? "…" : "") + ". ¿Está abierto Google Drive para ordenador?");
    }

    private static boolean exists(String folder) {
        try {
            return Files.isDirectory(Path.of(folder));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
