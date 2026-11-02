package com.socialhub.pieces;

import com.socialhub.production.TaskExecutor;
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
        SpecReader.Loaded loaded = reader.load(run.inputs().getFirst(), promotions.driveFolders(run.promotionId()));
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
}
