package com.socialhub.promotion;

import com.socialhub.promotion.domain.Promotion;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Lo que pasa después de guardar una promoción. Va aparte de {@link PromotionCommands} (que es una sola transacción) para que se llame cuando el guardado ya está confirmado: si falla, no queda nada pedido. */
@PrimaryPort
public interface PromotionSaveNotes {

    /** @return el aviso sobre las propuestas de la primera semana (pedidas ahora, cuándo se pedirán o qué las retiene), si lo hay */
    Optional<String> proposalsNote(Promotion promotion);
}
