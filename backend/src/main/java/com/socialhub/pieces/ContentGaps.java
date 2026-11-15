package com.socialhub.pieces;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Publicaciones «En producción» sin contenido (sin piezas) y fuera de cualquier spec abierto, para avisar en «Qué hacer
 * ahora» cuando alguna sale pronto. Incluye las que ya no se piden solas (tras dos re-pedidos sin éxito).
 */
@PrimaryPort
public interface ContentGaps {

    /** Una publicación que sale en menos de este tiempo sin contenido es una recomendación en «Qué hacer ahora». */
    Duration SOON = Duration.ofDays(3);

    /** Una publicación sin contenido. */
    record Gap(String publicationId, String title, Instant scheduledAt) {}

    /** Las de la promoción, por hora de publicación. */
    List<Gap> of(String promotionId);
}
