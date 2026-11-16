package com.socialhub.promotion;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Las promociones planificadas que ya han empezado pasan a activas. */
@PrimaryPort
public interface PromotionActivation {

    /**
     * Las promociones planificadas que ya han empezado (empiezan hoy o antes y no han terminado) pasan a activas.
     * @return los identificadores de las promociones activadas
     */
    List<String> activateStarted(java.time.LocalDate today);
}
