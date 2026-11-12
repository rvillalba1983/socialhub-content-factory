package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PublicationFileJpaRepository extends JpaRepository<PublicationFileEntity, PublicationFileEntity.Key> {
    @org.springframework.data.jpa.repository.Query(
            "select distinct f.publicationId from PublicationFileEntity f where f.publicationId in :ids")
    java.util.Set<String> withFiles(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

    List<PublicationFileEntity> findByPublicationIdOrderByAddedAtAscPathAsc(String publicationId);
}
