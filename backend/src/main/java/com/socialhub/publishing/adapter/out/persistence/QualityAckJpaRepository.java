package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface QualityAckJpaRepository extends JpaRepository<QualityEntities.AckEntity, QualityEntities.Key> {
    List<QualityEntities.AckEntity> findByPublicationId(String publicationId);
}
