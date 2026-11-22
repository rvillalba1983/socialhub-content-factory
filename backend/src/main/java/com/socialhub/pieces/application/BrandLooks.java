package com.socialhub.pieces.application;

import com.socialhub.pieces.domain.BrandLook;
import com.socialhub.pieces.domain.BrandMaterial;
import com.socialhub.promotion.PromotionQueries;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** El color y la portada de la marca de una promoción, para las piezas sin material propio. */
@Component
@RequiredArgsConstructor
class BrandLooks {

    private final PromotionQueries promotions;

    /** La portada: {@code portada.jpg}, {@code .jpeg} o {@code .png} (sin distinguir mayúsculas) en la raíz del Material de la marca. */
    Optional<Path> cover(String promotionId) {
        return promotions.brandMaterialFolder(promotions.brandOf(promotionId)).flatMap(folder -> {
            try {
                return BrandMaterial.cover(Path.of(folder));
            } catch (InvalidPathException e) {
                return Optional.empty();
            }
        });
    }

    BrandLook of(String promotionId) {
        String color = promotions.colorOf(promotions.brandOf(promotionId));
        return new BrandLook(color.matches("#[0-9A-Fa-f]{6}") ? color : null, cover(promotionId).orElse(null));
    }
}
