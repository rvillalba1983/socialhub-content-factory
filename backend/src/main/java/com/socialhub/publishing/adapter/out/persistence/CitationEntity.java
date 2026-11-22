package com.socialhub.publishing.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Tabla {@code publication_citation}. */
@Entity @Table(name = "publication_citation") @IdClass(CitationEntity.Key.class)
@Getter @NoArgsConstructor @AllArgsConstructor
class CitationEntity {

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String publicationId;
        private int position;
    }

    @Id private String publicationId;
    @Id private int position;
    private String text;
    private String sourceFile;
    private @Nullable String location;
}
