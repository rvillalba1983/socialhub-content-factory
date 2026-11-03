package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.ExternalCalendarRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ExternalPostJpaRepository extends JpaRepository<ExternalCalendarEntities.PostEntity, Long> {

    @Query("select p from ExternalPostEntity p where p.brandId = :brandId and p.at >= :from and p.at < :to order by p.at")
    List<ExternalCalendarEntities.PostEntity> between(String brandId, Instant from, Instant to);

    @Modifying
    @Query("delete from ExternalPostEntity p where p.brandId = :brandId and p.source = :source and p.at >= :from and p.at < :to")
    void deleteWindow(String brandId, ExternalCalendarRepository.Source source, Instant from, Instant to);
}
