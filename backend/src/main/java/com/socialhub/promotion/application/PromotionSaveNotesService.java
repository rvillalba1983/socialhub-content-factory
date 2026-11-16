package com.socialhub.promotion.application;

import com.socialhub.promotion.PromotionSaveNotes;
import com.socialhub.promotion.PromotionSavedHook;
import com.socialhub.promotion.domain.Promotion;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Lo que pasa después de guardar una promoción. Va aparte de {@link com.socialhub.promotion.PromotionCommands} (que es una sola transacción)
 * para que se llame cuando el guardado ya está confirmado: si falla, no queda nada pedido.
 */
@Service
@RequiredArgsConstructor
class PromotionSaveNotesService implements PromotionSaveNotes {

    private final PromotionSavedHook hook;

    /** @return el aviso sobre las propuestas de la primera semana (pedidas ahora, cuándo se pedirán o qué las retiene), si lo hay */
    @Override
    public Optional<String> proposalsNote(Promotion promotion) {
        return hook.afterSave(new PromotionSavedHook.Saved(promotion.id(), promotion.brandId(), promotion.status(),
                promotion.startsOn(), promotion.endsOn()));
    }
}
