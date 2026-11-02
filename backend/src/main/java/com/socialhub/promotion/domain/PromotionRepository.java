package com.socialhub.promotion.domain;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface PromotionRepository {

    List<Brand> brands();

    List<Promotion> promotions();

    Optional<Promotion> promotion(String id);

    /** Reglas que se aplican a todas las promociones. */
    List<String> commonRules();
}
