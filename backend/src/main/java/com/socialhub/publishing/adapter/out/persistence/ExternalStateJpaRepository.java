package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExternalStateJpaRepository extends JpaRepository<ExternalCalendarEntities.StateEntity, ExternalCalendarEntities.StateKey> {
    List<ExternalCalendarEntities.StateEntity> findByBrandIdOrderBySource(String brandId);
}
