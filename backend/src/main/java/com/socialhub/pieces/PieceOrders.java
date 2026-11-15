package com.socialhub.pieces;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/**
 * Encargos de piezas: desde el botón de la estación o desde la carpeta vigilada. La carpeta vigilada es la que tiene el
 * papel «Encargos»; si la promoción no tiene ese papel, {@code <primera carpeta de la promoción>/SocialHub/encargos}.
 */
@PrimaryPort
public interface PieceOrders {

    /** Subcarpeta (dentro de la primera carpeta de la promoción) que se vigila cuando no hay carpeta de «Encargos». */
    Path WATCHED = Path.of("SocialHub", "encargos");

    record SpecFile(String path, String name, Instant modifiedAt, @Nullable String taskId) {}

    record Watched(@Nullable String folder, List<SpecFile> items) {}

    /** spec.json de la carpeta vigilada de la promoción, con la tarea que generó cada uno si ya se encargó. */
    Watched specs(String promotionId);

    /** Encarga las piezas de un spec.json. Lo valida antes de crear la tarea. Devuelve el identificador de la tarea. */
    String order(String promotionId, String specPath);

    /** Convierte en tareas los spec.json nuevos (o modificados) de las carpetas vigiladas de todas las promociones. */
    void orderNewSpecs();
}
