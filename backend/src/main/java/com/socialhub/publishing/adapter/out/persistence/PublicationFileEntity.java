package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.PublicationFile;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tabla {@code publication_file}. */
@Entity
@Table(name = "publication_file")
@IdClass(PublicationFileEntity.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
class PublicationFileEntity {

    @Id private String publicationId;
    @Id private String path;

    @Enumerated(EnumType.STRING)
    private PublicationFile.Origin origin;

    private Instant addedAt;

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String publicationId;
        private String path;
    }
}
