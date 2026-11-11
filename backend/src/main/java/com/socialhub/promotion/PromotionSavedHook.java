package com.socialhub.promotion;

import java.time.LocalDate;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * Lo que debe pasar justo después de guardar una promoción (ya confirmada en la base de datos): pedir las propuestas
 * de su primera semana si ya toca. Lo implementa el módulo de IA.
 */
@SecondaryPort
public interface PromotionSavedHook {

    /** Lo que hace falta saber de la promoción guardada ({@code status}: PLANNED, ACTIVE, PAUSED o FINISHED). */
    record Saved(String id, String brandId, String status, LocalDate startsOn, LocalDate endsOn) {}

    /** @return el aviso que se enseña al guardar sobre sus propuestas, o vacío si no hay nada que decir */
    Optional<String> afterSave(Saved promotion);
}
