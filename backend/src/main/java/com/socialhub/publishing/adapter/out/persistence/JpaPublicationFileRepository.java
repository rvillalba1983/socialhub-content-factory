package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPublicationFileRepository implements PublicationFileRepository {

    private final PublicationFileJpaRepository jpa;

    @Override
    public List<PublicationFile> files(String publicationId) {
        return jpa.findByPublicationIdOrderByAddedAtAscPathAsc(publicationId).stream()
                .map(e -> new PublicationFile(e.getPath(), e.getOrigin(), e.getAddedAt()))
                .toList();
    }

    @Override
    public java.util.Set<String> withFiles(java.util.Collection<String> publicationIds) {
        return publicationIds.isEmpty() ? java.util.Set.of() : jpa.withFiles(publicationIds);
    }

    @Override
    public boolean add(String publicationId, PublicationFile file) {
        var key = new PublicationFileEntity.Key(publicationId, file.path());
        if (jpa.existsById(key)) return false;
        jpa.save(new PublicationFileEntity(publicationId, file.path(), file.origin(), file.addedAt()));
        return true;
    }

    @Override
    public boolean remove(String publicationId, String path) {
        var key = new PublicationFileEntity.Key(publicationId, path);
        if (!jpa.existsById(key)) return false;
        jpa.deleteById(key);
        return true;
    }
}
