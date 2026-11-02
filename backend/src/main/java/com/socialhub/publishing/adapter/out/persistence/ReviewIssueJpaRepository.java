package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ReviewIssueJpaRepository extends JpaRepository<QualityEntities.ReviewIssueEntity, QualityEntities.Key> {
    List<QualityEntities.ReviewIssueEntity> findByPublicationIdOrderByIssueKey(String publicationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from com.socialhub.publishing.adapter.out.persistence.QualityEntities$ReviewIssueEntity r where r.publicationId = :publicationId")
    void deleteByPublication(String publicationId);
}
