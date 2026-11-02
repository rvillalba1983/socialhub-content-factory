package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaStationRepository implements StationRepository {

    private final StationJpaRepository jpa;
    private final ProductionPersistenceMapper mapper;

    @Override
    public Optional<Station> findById(String id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Station> findAll() {
        return jpa.findAllByOrderByPosition().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Station save(Station station) {
        StationEntity entity = jpa.findById(station.getId())
                .orElseThrow(() -> new IllegalStateException("Las estaciones se crean con la migración o los datos de ejemplo"));
        mapper.update(station, entity);
        jpa.save(entity);
        return station;
    }
}
