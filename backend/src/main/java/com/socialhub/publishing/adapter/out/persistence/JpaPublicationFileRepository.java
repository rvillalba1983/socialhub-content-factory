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
                .map(e -> new PublicationFile(e.getPath(), e.getOrigin(), e.getAddedAt(), e.getPage(), e.getSourceName()))
                .toList();
    }

    @Override
    public java.util.Set<String> withFiles(java.util.Collection<String> publicationIds) {
        return publicationIds.isEmpty() ? java.util.Set.of() : jpa.withFiles(publicationIds);
    }

    @Override
    public boolean add(String publicationId, PublicationFile file) {
        var key = new PublicationFileEntity.Key(publicationId, file.path());
        var existing = jpa.findById(key);
        if (existing.isPresent()) {
            // Una pieza regenerada ya asociada (también las de antes de V36) pone al día su página y su archivo de origen
            var old = existing.get();
            if (file.origin() == PublicationFile.Origin.SPEC && (file.page() != null || file.sourceName() != null)
                    && !(java.util.Objects.equals(old.getPage(), file.page()) && java.util.Objects.equals(old.getSourceName(), file.sourceName()))) {
                jpa.save(new PublicationFileEntity(publicationId, old.getPath(), old.getOrigin(), old.getAddedAt(), file.page(), file.sourceName()));
            }
            return false;
        }
        jpa.save(new PublicationFileEntity(publicationId, file.path(), file.origin(), file.addedAt(), file.page(), file.sourceName()));
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
