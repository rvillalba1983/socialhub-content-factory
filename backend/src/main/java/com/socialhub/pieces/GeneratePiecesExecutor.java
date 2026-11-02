package com.socialhub.pieces;

import com.socialhub.production.ResourceUnavailableException;
import com.socialhub.production.TaskExecutor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tarea {@code GENERATE_PIECES}: genera las piezas de un spec.json (lo que hacía promo.py). */
@Component
@RequiredArgsConstructor
class GeneratePiecesExecutor implements TaskExecutor {

    static final String TYPE = "GENERATE_PIECES";
    static final String AGENT = "Piezas · Java 2D y FFmpeg";

    private final SpecReader reader;
    private final PieceGenerator generator;
    private final PromotionQueries promotions;
    private final PiecePublications publications;
    private final PublishingService publishing;

    @Override public String type() { return TYPE; }
    @Override public String agentName() { return AGENT; }
    @Override public Duration timeout() { return Duration.ofMinutes(45); }

    @Override
    public String run(Run run) throws Exception {
        if (run.promotionId() == null) throw new IllegalStateException("La tarea no tiene promoción");
        if (run.inputs().isEmpty()) throw new IllegalStateException("La tarea no indica el spec.json");
        List<String> folders = promotions.driveFolders(run.promotionId());
        requireFolderOf(run.inputs().getFirst(), folders);
        SpecReader.Loaded loaded = reader.load(run.inputs().getFirst(), folders);
        publications.check(run.promotionId(), loaded.spec());
        run.log("Spec leído: " + loaded.spec().piezas().size() + " piezas");
        PieceGenerator.Batch batch = generator.generate(loaded.spec(), loaded.files(), run::log, run::progress);
        int files = batch.pieces().stream().mapToInt(p -> p.files().size()).sum();
        for (PieceGenerator.PieceResult piece : batch.pieces()) {
            if (piece.publication() == null || piece.files().isEmpty()) continue;
            int added = publishing.attachGenerated(run.promotionId(), piece.publication(), piece.files());
            run.log(piece.name() + ": " + added + " archivos asociados a la publicación " + piece.publication());
        }
        return batch.pieces().size() + " piezas (" + files + " archivos) en " + batch.outputDir()
                + (batch.readme() == null ? "" : " · LEEME: " + batch.readme().getFileName());
    }

    /**
     * Si la carpeta de la promoción que contiene el spec no está (G: sin montar), la tarea se bloquea y espera
     * en lugar de fallar.
     */
    static void requireFolderOf(String specPath, List<String> folders) {
        Path spec;
        try {
            spec = canonical(Path.of(specPath));
        } catch (InvalidPathException e) {
            return;
        }
        for (String folder : folders) {
            Path root;
            try {
                root = canonical(Path.of(folder));
            } catch (InvalidPathException e) {
                continue;
            }
            if (spec.startsWith(root) && !Files.isDirectory(root)) {
                throw new ResourceUnavailableException(folder, "La carpeta «" + folder + "» no está disponible en este "
                        + "ordenador. ¿Está abierto Google Drive para ordenador? La tarea seguirá sola cuando aparezca.");
            }
        }
    }

    /**
     * Ruta comparable aunque no exista: la parte que existe se pasa a su ruta real y se le añade el resto. Así,
     * en Windows, «RUNNER~1» y «runneradmin» (nombres cortos y largos) acaban igual.
     */
    static Path canonical(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) return absolute;
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        } catch (java.io.IOException e) {
            return absolute;
        }
    }
}
