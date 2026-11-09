package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.PublicationStatus;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PublicationJpaRepository
        extends JpaRepository<PublicationEntity, String>, JpaSpecificationExecutor<PublicationEntity> {

    Sort CALENDAR = Sort.by("scheduledOn", "scheduledTime", "id");

    List<PublicationEntity> findByPromotionIdAndStatus(String promotionId, PublicationStatus status);

    @org.springframework.data.jpa.repository.Query(
            "select p.promotionId, count(p) from PublicationEntity p where p.status = :status group by p.promotionId")
    List<Object[]> countByPromotion(@org.springframework.data.repository.query.Param("status") PublicationStatus status);

    List<PublicationEntity> findByPromotionIdAndFlagIsNotNull(String promotionId);
}
