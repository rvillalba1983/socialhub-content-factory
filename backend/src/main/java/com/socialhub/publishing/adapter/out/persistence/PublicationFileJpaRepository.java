package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PublicationFileJpaRepository extends JpaRepository<PublicationFileEntity, PublicationFileEntity.Key> {
    List<PublicationFileEntity> findByPublicationIdOrderByAddedAtAscPathAsc(String publicationId);
}
