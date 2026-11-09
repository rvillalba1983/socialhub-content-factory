package com.socialhub.publishing.domain;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Puerto de salida: dónde se guardan las publicaciones. */
@SecondaryPort
public interface PublicationRepository {

    /** Página del calendario, ordenada por fecha y hora. */
    PublicationPage find(PublicationFilter filter, long offset, int limit);

    /** Todas, ordenadas por fecha y hora (vista general del anillo). */
    List<Publication> findAll();

    /** Cuántas publicaciones hay en ese estado por promoción (solo las promociones que tienen alguna). */
    java.util.Map<String, Long> countByPromotion(PublicationStatus status);

    List<Publication> findByPromotionAndStatus(String promotionId, PublicationStatus status);

    List<Publication> findFlagged(String promotionId);

    Optional<Publication> findById(String id);

    boolean exists(String id);

    Publication save(Publication publication);
}
