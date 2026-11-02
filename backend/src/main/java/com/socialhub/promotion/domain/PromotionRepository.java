package com.socialhub.promotion.domain;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface PromotionRepository {

    List<Brand> brands();

    List<Promotion> promotions();

    Optional<Promotion> promotion(String id);

    Optional<Brand> brand(String id);

    /** Reglas que se aplican a todas las promociones. */
    List<String> commonRules();

    boolean brandIdTaken(String id);

    boolean promotionIdTaken(String id);

    /** Crea o actualiza. Las nuevas van al final de la lista. */
    Brand save(Brand brand);

    /** Crea o actualiza. Las nuevas van al final de la lista. */
    Promotion save(Promotion promotion);
}
