package com.socialhub.pieces;

import com.socialhub.publishing.PublishingQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Comprueba que las publicaciones que indica el spec.json existen y son de la promoción. */
@Component
@RequiredArgsConstructor
class PiecePublications {

    private final PublishingQueries publications;

    void check(String promotionId, PromoSpec spec) {
        for (PromoSpec.Piece piece : spec.piezas()) {
            String id = piece.publicacion();
            if (id == null || id.isBlank()) continue;
            if (!publications.belongsTo(promotionId, id.strip())) {
                throw new IllegalArgumentException("Pieza «" + piece.nombre() + "»: la publicación «" + id.strip()
                        + "» no existe en esta promoción");
            }
        }
    }
}
