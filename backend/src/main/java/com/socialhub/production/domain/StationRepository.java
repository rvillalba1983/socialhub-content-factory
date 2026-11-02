package com.socialhub.production.domain;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Puerto de salida: dónde se guardan las estaciones. */
@SecondaryPort
public interface StationRepository {

    Optional<Station> findById(String id);

    /** Todas, en el orden del anillo. */
    List<Station> findAll();

    Station save(Station station);
}
