package com.socialhub.production.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface StationJpaRepository extends JpaRepository<StationEntity, String> {
    List<StationEntity> findAllByOrderByPosition();
}
