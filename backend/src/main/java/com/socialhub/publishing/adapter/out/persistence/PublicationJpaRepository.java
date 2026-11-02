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

    List<PublicationEntity> findByPromotionIdAndFlagIsNotNull(String promotionId);
}
