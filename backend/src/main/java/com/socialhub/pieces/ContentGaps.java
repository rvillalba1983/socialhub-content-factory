package com.socialhub.pieces;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Publicaciones «En producción» sin contenido (sin piezas) y fuera de cualquier spec abierto, para avisar en «Qué hacer
 * ahora» cuando alguna sale pronto. Incluye las que ya no se piden solas (tras dos re-pedidos sin éxito).
 */
@Service
@RequiredArgsConstructor
public class ContentGaps {

    /** Una publicación que sale en menos de este tiempo sin contenido es una recomendación en «Qué hacer ahora». */
    public static final java.time.Duration SOON = java.time.Duration.ofDays(3);

    /** Una publicación sin contenido. */
    public record Gap(String publicationId, String title, Instant scheduledAt) {}

    private final WeeklySpecHandler specs;
    private final Clock clock;

    /** Las de la promoción, por hora de publicación. */
    public List<Gap> of(String promotionId) {
        return specs.gaps(promotionId, clock.instant()).stream()
                .map(g -> new Gap(g.publicationId(), g.title(), g.scheduledAt())).toList();
    }
}
