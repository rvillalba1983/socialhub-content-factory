package com.socialhub.production.domain;

import java.util.List;
import java.util.Optional;

public interface StationRepository {

    Optional<Station> findById(String id);

    List<Station> findAll();

    void save(Station station);
}
