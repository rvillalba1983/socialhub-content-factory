package com.socialhub.pieces;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionService;
import com.socialhub.production.TaskRequest;
import com.socialhub.promotion.PromotionQueries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encargos de piezas: desde el botón de la estación o desde la carpeta vigilada. La carpeta vigilada es la que tiene el
 * papel «Encargos»; si la promoción no tiene ese papel, {@code <primera carpeta de la promoción>/SocialHub/encargos}.
 */
@Slf4j
@PrimaryPort
@Service
@RequiredArgsConstructor
public class PieceOrders {

    static final Path WATCHED = Path.of("SocialHub", "encargos");

    private final PromotionQueries promotions;
    private final ProductionService production;
    private final SpecReader reader;
    private final PieceOrderRepository orders;
    private final Clock clock;

    public record SpecFile(String path, String name, Instant modifiedAt, @Nullable String taskId) {}

    public record Watched(@Nullable String folder, List<SpecFile> items) {}

    /** spec.json de la carpeta vigilada de la promoción, con la tarea que generó cada uno si ya se encargó. */
    @Transactional(readOnly = true)
    public Watched specs(String promotionId) {
        Path folder = watchedFolder(promotionId);
        if (folder == null || !Files.isDirectory(folder)) return new Watched(folder == null ? null : folder.toString(), List.of());
        try (Stream<Path> files = Files.list(folder)) {
            List<SpecFile> items = files
                    .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .map(f -> {
                        Instant modified = modified(f);
                        String task = orders.findFirstBySpecPathAndSpecModifiedAt(f.toString(), modified)
                                .map(PieceOrderEntity::getTaskId).orElse(null);
                        return new SpecFile(f.toString(), f.getFileName().toString(), modified, task);
                    })
                    .toList();
            return new Watched(folder.toString(), items);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se puede leer la carpeta " + folder + ": " + e.getMessage());
        }
    }

    /** Encarga las piezas de un spec.json. Lo valida antes de crear la tarea. Devuelve el identificador de la tarea. */
    @Transactional
    public String order(String promotionId, String specPath) {
        SpecReader.Loaded loaded = reader.load(specPath, promotions.driveFolders(promotionId));
        return submit(promotionId, loaded.path(), loaded.spec().piezas().size() + " piezas");
    }

    private String submit(String promotionId, Path spec, String detail) {
        String taskId = production.submitTask(new TaskRequest("gen", promotionId, GeneratePiecesExecutor.TYPE,
                "Piezas · " + spec.getFileName(),
                "Generar las piezas del spec (" + detail + ") en las carpetas de la promoción, sin sobrescribir nada.",
                List.of(spec.toString()), List.of()), GeneratePiecesExecutor.AGENT);
        orders.save(new PieceOrderEntity(promotionId, spec.toString(), modified(spec), taskId, clock.instant()));
        return taskId;
    }

    /**
     * Cada 30 s, en modo real: los spec.json nuevos (o modificados) de las carpetas vigiladas se convierten en
     * tareas. Si un spec no es válido, la tarea falla con el motivo y queda a la vista.
     */
    @Scheduled(initialDelayString = "${socialhub.pieces.watch-initial-delay-millis:15000}",
               fixedDelayString = "${socialhub.pieces.watch-millis:30000}")
    void watch() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            for (String promotionId : promotions.promotionIds()) {
                try {
                    for (SpecFile spec : specs(promotionId).items()) {
                        if (spec.taskId() == null && isNew(spec)) {
                            log.info("Encargo nuevo en la carpeta vigilada: {}", spec.path());
                            submit(promotionId, Path.of(spec.path()), "desde la carpeta vigilada");
                        }
                    }
                } catch (RuntimeException e) {
                    log.debug("Carpeta vigilada de {} omitida: {}", promotionId, e.getMessage());
                }
            }
        });
    }

    private boolean isNew(SpecFile spec) {
        return orders.findFirstBySpecPathAndSpecModifiedAt(spec.path(), spec.modifiedAt()).isEmpty();
    }

    private @Nullable Path watchedFolder(String promotionId) {
        var orders = promotions.ordersFolder(promotionId);
        if (orders.isPresent()) return Path.of(orders.get());
        List<String> folders = promotions.driveFolders(promotionId);
        return folders.isEmpty() ? null : Path.of(folders.getFirst()).resolve(WATCHED);
    }

    private static Instant modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().truncatedTo(ChronoUnit.MILLIS);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se puede leer " + file);
        }
    }
}
