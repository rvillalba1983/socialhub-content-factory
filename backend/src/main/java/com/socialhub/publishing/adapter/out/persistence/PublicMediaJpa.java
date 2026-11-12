package com.socialhub.publishing.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PublicMediaJpa extends JpaRepository<InstagramPersistence.MediaEntity, InstagramPersistence.MediaEntity.Key> {
    List<InstagramPersistence.MediaEntity> findByPublicationIdOrderByPosition(String publicationId);

    Optional<InstagramPersistence.MediaEntity> findByFileName(String fileName);

    @Modifying
    @Query("delete from PublicMediaRow m where m.publicationId = :publicationId")
    void deleteByPublication(String publicationId);
}
