package com.socialhub.pieces.application;

import com.socialhub.pieces.domain.PromoSpec;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingQueries;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Prepara lo que el generador necesita de fuera del spec: la marca de la promoción y la red de cada publicación. */
@Component
@RequiredArgsConstructor
class GenerationSetups {

    private final PromotionQueries promotions;
    private final PublishingQueries publications;

    GenerationSetup of(String promotionId, PromoSpec spec) {
        List<String> ids = spec.piezas().stream().map(PromoSpec.Piece::publicacion)
                .filter(id -> id != null && !id.isBlank()).map(String::strip).distinct().toList();
        Map<String, PublishingQueries.Network> networks = publications.networks(ids);
        return new GenerationSetup(promotions.brandOf(promotionId), id -> networks.get(id) == PublishingQueries.Network.TIKTOK);
    }
}
