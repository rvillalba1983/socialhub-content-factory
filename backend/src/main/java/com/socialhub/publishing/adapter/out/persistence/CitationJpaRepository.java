package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CitationJpaRepository extends JpaRepository<CitationEntity, CitationEntity.Key> {
    List<CitationEntity> findByPublicationIdOrderByPosition(String publicationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CitationEntity c where c.publicationId = :publicationId")
    void deleteByPublication(String publicationId);
}
