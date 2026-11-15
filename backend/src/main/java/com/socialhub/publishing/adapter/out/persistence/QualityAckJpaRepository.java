package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface QualityAckJpaRepository extends JpaRepository<QualityEntities.AckEntity, QualityEntities.Key> {
    List<QualityEntities.AckEntity> findByPublicationId(String publicationId);

    @Modifying(flushAutomatically = true)
    @Query("delete from com.socialhub.publishing.adapter.out.persistence.QualityEntities$AckEntity a where a.publicationId = :publicationId")
    void deleteByPublication(String publicationId);
}
